package io.github.chrisshi.mom.iam.application.admin;

import io.github.chrisshi.mom.iam.domain.exception.IamDomainConflictException;
import io.github.chrisshi.mom.iam.domain.exception.IamStaleVersionException;

import java.util.function.Supplier;

/** Domain 失败到稳定 Application 失败的单向边界映射。 */
public final class IamAdminFailures {
    private IamAdminFailures() { }

    public static <T> T fromDomain(Supplier<T> operation) {
        try {
            return operation.get();
        }
        catch (IamStaleVersionException exception) {
            throw new IamAdminExceptions.StaleVersion(exception.getMessage());
        }
        catch (IamDomainConflictException exception) {
            throw new IamAdminExceptions.Conflict(exception.getMessage());
        }
    }

    public static void fromDomain(Runnable operation) {
        fromDomain(() -> {
            operation.run();
            return null;
        });
    }
}
