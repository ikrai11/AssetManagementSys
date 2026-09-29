package com.ikrai.project.service.stats.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.AssetStatus;
import com.ikrai.project.common.enums.BorrowOrderStatus;
import com.ikrai.project.dao.AssetCategoryDao;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.BorrowOrderDao;
import com.ikrai.project.dataobject.AssetCategoryDO;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.dataobject.BorrowOrderDO;
import com.ikrai.project.service.stats.StatsService;
import com.ikrai.project.vo.StatsNameCountVO;
import com.ikrai.project.vo.StatsOverviewVO;
import com.ikrai.project.vo.StatsTrendVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class StatsServiceImpl implements StatsService {

    private static final List<String> APPLYING = List.of(
            BorrowOrderStatus.DRAFT.name(),
            BorrowOrderStatus.PENDING.name(),
            BorrowOrderStatus.APPROVED.name()
    );
    private static final List<String> USING = List.of(
            BorrowOrderStatus.BORROWING.name(),
            BorrowOrderStatus.RETURN_PENDING.name()
    );

    private final AssetDao assetDao;
    private final AssetCategoryDao assetCategoryDao;
    private final BorrowOrderDao borrowOrderDao;

    public StatsServiceImpl(AssetDao assetDao, AssetCategoryDao assetCategoryDao, BorrowOrderDao borrowOrderDao) {
        this.assetDao = assetDao;
        this.assetCategoryDao = assetCategoryDao;
        this.borrowOrderDao = borrowOrderDao;
    }

    @Override
    public StatsOverviewVO overview(AuthUser viewer) {
        StatsOverviewVO vo = new StatsOverviewVO();
        LocalDate today = LocalDate.now();
        LocalDate dueUntil = today.plusDays(7);
        if (viewer.isAdmin()) {
            vo.setTotal(countAssets(null, null, null, null));
            vo.setInStock(countAssets(AssetStatus.IN_STOCK.name(), null, null, null));
            vo.setPending(countAssets(AssetStatus.PENDING.name(), null, null, null));
            vo.setBorrowed(countAssets(AssetStatus.BORROWED.name(), null, null, null));
            vo.setRepairing(countAssets(AssetStatus.REPAIRING.name(), null, null, null));
            vo.setScrapped(countAssets(AssetStatus.SCRAPPED.name(), null, null, null));
            vo.setDueSoon(countAssets(AssetStatus.BORROWED.name(), today, dueUntil, null));
            vo.setOverdue(countAssets(AssetStatus.BORROWED.name(), null, today.minusDays(1), null));
            vo.setPendingApproval(countOrders(List.of(BorrowOrderStatus.PENDING.name()), null));
            return vo;
        }
        vo.setMyApplying(countOrders(APPLYING, viewer.getUserId()));
        vo.setMyUsing(countOrders(USING, viewer.getUserId()));
        vo.setMyDueSoon(countAssets(AssetStatus.BORROWED.name(), today, dueUntil, viewer.getUserId()));
        vo.setMyOverdue(countAssets(AssetStatus.BORROWED.name(), null, today.minusDays(1), viewer.getUserId()));
        return vo;
    }

    @Override
    public List<StatsNameCountVO> byCategory(AuthUser viewer) {
        var wrapper = Wrappers.<AssetDO>lambdaQuery();
        if (viewer.isAdmin()) {
            wrapper.select(AssetDO::getCategoryId);
        } else {
            wrapper.eq(AssetDO::getStatus, AssetStatus.BORROWED.name())
                    .eq(AssetDO::getHolderUserId, viewer.getUserId());
        }
        List<AssetDO> assets = assetDao.selectList(wrapper);
        Map<Long, Long> counts = assets.stream()
                .map(AssetDO::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));
        if (counts.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = assetCategoryDao.selectList(Wrappers.<AssetCategoryDO>lambdaQuery()
                        .in(AssetCategoryDO::getId, counts.keySet()))
                .stream()
                .collect(Collectors.toMap(AssetCategoryDO::getId, AssetCategoryDO::getName));
        return counts.entrySet().stream()
                .map(entry -> new StatsNameCountVO(entry.getKey(), names.getOrDefault(entry.getKey(), "未分类"), entry.getValue()))
                .toList();
    }

    @Override
    public List<StatsTrendVO> borrowTrend(AuthUser viewer) {
        YearMonth start = YearMonth.now().minusMonths(5);
        LocalDateTime from = start.atDay(1).atStartOfDay();
        var wrapper = Wrappers.<BorrowOrderDO>lambdaQuery()
                .isNotNull(BorrowOrderDO::getIssuedAt)
                .ge(BorrowOrderDO::getIssuedAt, from);
        if (!viewer.isAdmin()) {
            wrapper.eq(BorrowOrderDO::getApplicantId, viewer.getUserId());
        }
        Map<YearMonth, Long> grouped = borrowOrderDao.selectList(wrapper).stream()
                .collect(Collectors.groupingBy(order -> YearMonth.from(order.getIssuedAt()), Collectors.counting()));
        List<StatsTrendVO> result = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            YearMonth month = start.plusMonths(i);
            result.add(new StatsTrendVO(month.toString(), grouped.getOrDefault(month, 0L)));
        }
        return result;
    }

    private long countAssets(String status, LocalDate dueFrom, LocalDate dueTo, Long holderId) {
        var wrapper = Wrappers.<AssetDO>lambdaQuery();
        if (status != null) {
            wrapper.eq(AssetDO::getStatus, status);
        }
        if (dueFrom != null) {
            wrapper.ge(AssetDO::getExpectedReturnDate, dueFrom);
        }
        if (dueTo != null) {
            wrapper.le(AssetDO::getExpectedReturnDate, dueTo);
        }
        if (holderId != null) {
            wrapper.eq(AssetDO::getHolderUserId, holderId);
        }
        Long count = assetDao.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    private long countOrders(List<String> statuses, Long applicantId) {
        var wrapper = Wrappers.<BorrowOrderDO>lambdaQuery();
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in(BorrowOrderDO::getStatus, statuses);
        }
        if (applicantId != null) {
            wrapper.eq(BorrowOrderDO::getApplicantId, applicantId);
        }
        Long count = borrowOrderDao.selectCount(wrapper);
        return count == null ? 0 : count;
    }
}
