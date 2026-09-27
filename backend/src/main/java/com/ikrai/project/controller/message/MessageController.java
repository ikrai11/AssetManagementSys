package com.ikrai.project.controller.message;

import com.ikrai.project.common.result.Result;
import com.ikrai.project.config.SecurityUsers;
import com.ikrai.project.dto.MessageReadDTO;
import com.ikrai.project.query.MessageQuery;
import com.ikrai.project.service.message.MessageService;
import com.ikrai.project.vo.PageVO;
import com.ikrai.project.vo.SiteMessageVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public Result<PageVO<SiteMessageVO>> list(MessageQuery query) {
        return Result.ok(messageService.list(SecurityUsers.current(), query));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(messageService.unreadCount(SecurityUsers.current()));
    }

    @PostMapping("/read")
    public Result<Void> markRead(@RequestBody(required = false) MessageReadDTO dto) {
        messageService.markRead(SecurityUsers.current(), dto);
        return Result.ok(null);
    }
}
