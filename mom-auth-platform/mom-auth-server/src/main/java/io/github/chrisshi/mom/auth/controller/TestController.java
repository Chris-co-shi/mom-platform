package io.github.chrisshi.mom.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author 史偕成
 * @date 2026/09/03 17:25
 **/
@RestController
public class TestController {

    @GetMapping("/test")
    public String test() {
        return "hello world";
    }
}
