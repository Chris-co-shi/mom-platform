-- P1.5 S02 四个稳定 Client Policy；不写入环境相关 Redirect URI 或 Registered Client。
INSERT INTO iam_oauth_client_policy (
    id, client_id, application_code, channel, allowed_user_type, mobile_access_required,
    status, description, created_at, created_by, updated_at, updated_by, version
) VALUES
('1200000000000000001', 'mom-admin-web', 'MOM_ADMIN', 'WEB', 'INTERNAL', false, 'ENABLED',
 'MOM Admin Public Client Policy，仅允许 INTERNAL 用户', now(), 'mom-iam-flyway', now(), 'mom-iam-flyway', 0),
('1200000000000000002', 'mom-supplier-web', 'MOM_SUPPLIER_PORTAL', 'WEB', 'SUPPLIER', false, 'ENABLED',
 'Supplier Portal Public Client Policy，仅允许 SUPPLIER 用户', now(), 'mom-iam-flyway', now(), 'mom-iam-flyway', 0),
('1200000000000000003', 'mom-customer-web', 'MOM_CUSTOMER_PORTAL', 'WEB', 'CUSTOMER', false, 'ENABLED',
 'Customer Portal Public Client Policy，仅允许 CUSTOMER 用户', now(), 'mom-iam-flyway', now(), 'mom-iam-flyway', 0),
('1200000000000000004', 'mom-mobile-pda', 'MOM_MOBILE_PDA', 'MOBILE', 'INTERNAL', true, 'ENABLED',
 'MOM Mobile PDA Public Client Policy，仅允许具备 Mobile Access 的 INTERNAL 用户', now(), 'mom-iam-flyway', now(), 'mom-iam-flyway', 0);
