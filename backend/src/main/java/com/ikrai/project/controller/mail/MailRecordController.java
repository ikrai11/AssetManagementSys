package com.ikrai.project.controller.mail;

import com.ikrai.project.common.result.Result;
import com.ikrai.project.config.SecurityUsers;
import com.ikrai.project.query.MailRecordQuery;
import com.ikrai.project.service.mail.MailRecordService;
import com.ikrai.project.vo.MailRecordVO;
import com.ikrai.project.vo.PageVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail-records")
@PreAuthorize("hasRole('ADMIN')")
public class MailRecordController {

    private final MailRecordService mailRecordService;

    public MailRecordController(MailRecordService mailRecordService) {
        this.mailRecordService = mailRecordService;
    }

    @GetMapping
    public Result<PageVO<MailRecordVO>> list(MailRecordQuery query) {
        return Result.ok(mailRecordService.list(SecurityUsers.current(), query));
    }

    @PostMapping("/{id}/retry")
    public Result<MailRecordVO> retry(@PathVariable Long id) {
        return Result.ok(mailRecordService.retry(SecurityUsers.current(), id));
    }
}
