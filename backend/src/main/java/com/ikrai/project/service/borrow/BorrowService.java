package com.ikrai.project.service.borrow;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.dto.BorrowCreateDTO;
import com.ikrai.project.query.BorrowQuery;
import com.ikrai.project.vo.BorrowOrderVO;
import com.ikrai.project.vo.BorrowTodoVO;
import com.ikrai.project.vo.PageVO;

public interface BorrowService {

    BorrowOrderVO save(AuthUser applicant, BorrowCreateDTO dto);

    BorrowOrderVO submit(AuthUser applicant, Long orderId);

    BorrowOrderVO withdraw(AuthUser applicant, Long orderId);

    BorrowOrderVO approve(AuthUser admin, Long orderId);

    BorrowOrderVO reject(AuthUser admin, Long orderId, String comment);

    BorrowOrderVO issue(AuthUser admin, Long orderId);

    BorrowOrderVO requestReturn(AuthUser applicant, Long orderId);

    BorrowOrderVO confirmReturn(AuthUser admin, Long orderId, String comment);

    BorrowOrderVO get(AuthUser viewer, Long orderId);

    PageVO<BorrowOrderVO> list(AuthUser viewer, BorrowQuery query);

    BorrowTodoVO listTodos();
}
