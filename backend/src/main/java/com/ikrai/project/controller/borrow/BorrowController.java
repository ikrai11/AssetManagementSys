package com.ikrai.project.controller.borrow;

import com.ikrai.project.common.result.Result;
import com.ikrai.project.config.SecurityUsers;
import com.ikrai.project.dto.BorrowCreateDTO;
import com.ikrai.project.dto.RejectDTO;
import com.ikrai.project.dto.RenewApplyDTO;
import com.ikrai.project.dto.ReturnConfirmDTO;
import com.ikrai.project.query.BorrowQuery;
import com.ikrai.project.service.borrow.BorrowService;
import com.ikrai.project.vo.BorrowOrderVO;
import com.ikrai.project.vo.BorrowRenewVO;
import com.ikrai.project.vo.BorrowTodoVO;
import com.ikrai.project.vo.PageVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/borrows")
public class BorrowController {

    private final BorrowService borrowService;

    public BorrowController(BorrowService borrowService) {
        this.borrowService = borrowService;
    }

    @PostMapping
    public Result<BorrowOrderVO> save(@Valid @RequestBody BorrowCreateDTO dto) {
        return Result.ok(borrowService.save(SecurityUsers.current(), dto));
    }

    @GetMapping
    public Result<PageVO<BorrowOrderVO>> list(BorrowQuery query) {
        return Result.ok(borrowService.list(SecurityUsers.current(), query));
    }

    @GetMapping("/todos")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<BorrowTodoVO> todos() {
        return Result.ok(borrowService.listTodos());
    }

    @GetMapping("/{id}")
    public Result<BorrowOrderVO> get(@PathVariable Long id) {
        return Result.ok(borrowService.get(SecurityUsers.current(), id));
    }

    @PostMapping("/{id}/submit")
    public Result<BorrowOrderVO> submit(@PathVariable Long id) {
        return Result.ok(borrowService.submit(SecurityUsers.current(), id));
    }

    @PostMapping("/{id}/withdraw")
    public Result<BorrowOrderVO> withdraw(@PathVariable Long id) {
        return Result.ok(borrowService.withdraw(SecurityUsers.current(), id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<BorrowOrderVO> approve(@PathVariable Long id) {
        return Result.ok(borrowService.approve(SecurityUsers.current(), id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<BorrowOrderVO> reject(@PathVariable Long id, @RequestBody(required = false) RejectDTO dto) {
        String comment = dto == null ? null : dto.getComment();
        return Result.ok(borrowService.reject(SecurityUsers.current(), id, comment));
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<BorrowOrderVO> issue(@PathVariable Long id) {
        return Result.ok(borrowService.issue(SecurityUsers.current(), id));
    }

    @PostMapping("/{id}/renew")
    public Result<BorrowRenewVO> applyRenew(@PathVariable Long id, @Valid @RequestBody RenewApplyDTO dto) {
        return Result.ok(borrowService.applyRenew(SecurityUsers.current(), id, dto));
    }

    @PostMapping("/renews/{renewId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<BorrowRenewVO> approveRenew(@PathVariable Long renewId) {
        return Result.ok(borrowService.approveRenew(SecurityUsers.current(), renewId));
    }

    @PostMapping("/renews/{renewId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<BorrowRenewVO> rejectRenew(@PathVariable Long renewId, @RequestBody(required = false) RejectDTO dto) {
        String comment = dto == null ? null : dto.getComment();
        return Result.ok(borrowService.rejectRenew(SecurityUsers.current(), renewId, comment));
    }

    @PostMapping("/{id}/return-request")
    public Result<BorrowOrderVO> requestReturn(@PathVariable Long id) {
        return Result.ok(borrowService.requestReturn(SecurityUsers.current(), id));
    }

    @PostMapping("/{id}/return-confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<BorrowOrderVO> confirmReturn(@PathVariable Long id, @RequestBody(required = false) ReturnConfirmDTO dto) {
        String comment = dto == null ? null : dto.getComment();
        return Result.ok(borrowService.confirmReturn(SecurityUsers.current(), id, comment));
    }
}
