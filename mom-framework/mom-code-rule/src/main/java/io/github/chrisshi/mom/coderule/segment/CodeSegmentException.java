package io.github.chrisshi.mom.coderule.segment;

/**
 * 编码片段元数据、配置或查找失败。
 */
public class CodeSegmentException extends IllegalArgumentException {
    public CodeSegmentException(String message) {
        super(message);
    }
}
