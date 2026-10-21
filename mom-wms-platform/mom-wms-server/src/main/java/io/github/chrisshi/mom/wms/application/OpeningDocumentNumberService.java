package io.github.chrisshi.mom.wms.application;

import io.github.chrisshi.mom.coderule.application.CodeGenerationApplication;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** WMS 业务单据在自身本地事务中调用公共正式发号契约。 */
@Service
@RequiredArgsConstructor
public class OpeningDocumentNumberService {
    private static final String OPENING_RULE = "wms.opening.document";
    private final CodeGenerationApplication generation;

    /** 期初建账使用单据 ID 作为稳定业务请求键。 */
    public String allocate(String documentId) {
        return allocate(OPENING_RULE, documentId);
    }

    /** 在当前 WMS 本地事务中发号，随单据写入一起提交或回滚。 */
    public String allocate(String ruleCode, String businessRequestKey) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("WMS 正式发号必须处于本地事务中");
        return generation.generate(ruleCode, Map.of(), "business:" + hash(ruleCode + "\u0000" + businessRequestKey)).code();
    }

    private static String hash(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("业务发号请求键不能为空");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
