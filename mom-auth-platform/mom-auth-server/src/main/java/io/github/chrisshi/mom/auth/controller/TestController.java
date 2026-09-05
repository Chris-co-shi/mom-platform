package io.github.chrisshi.mom.auth.controller;

import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅用于 Gateway/Auth 联调与限流验证的公开测试端点。 */
@RestController
public class TestController {

    @GetMapping("/test")
    public Result<String> test() {
        return Result.success("hello world");
    }
}
