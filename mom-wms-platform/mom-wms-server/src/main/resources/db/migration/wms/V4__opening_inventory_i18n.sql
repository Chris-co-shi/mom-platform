WITH messages(message_key,zh_text,en_text) AS (VALUES
 ('wms.opening.title','期初库存建账','Opening Inventory'),
 ('wms.opening.hint','按仓库录入现有库存；过账后先核对，再单独启用仓库','Enter existing stock by warehouse. Reconcile after posting, then activate the warehouse.'),
 ('wms.opening.detail','期初建账详情','Opening Document Details'),
 ('wms.opening.documentno','单据编号','Document Number'),
 ('wms.opening.warehouse','仓库','Warehouse'),
 ('wms.opening.status','状态','Status'),
 ('wms.opening.transaction','库存交易','Inventory Transaction'),
 ('wms.opening.draft','草稿','Draft'),
 ('wms.opening.posted','已过账','Posted'),
 ('wms.opening.create','创建草稿','Create Draft'),
 ('wms.opening.selectwarehouse','选择仓库','Select Warehouse'),
 ('wms.opening.open','查看','Open'),
 ('wms.opening.forbidden','没有执行此操作的权限','You do not have permission to perform this action.'),
 ('wms.opening.loadfailed','期初建账读取失败','Could not load opening inventory.'),
 ('wms.opening.writefailed','保存失败，请刷新后核对单据状态','Save failed. Refresh and verify the document state.'),
 ('wms.opening.referenceloadfailed','无法读取物料或库位主数据','Could not load material or location master data.'),
 ('wms.opening.material','物料','Material'),
 ('wms.opening.batch','批次','Batch'),
 ('wms.opening.sn','SN 标签','SN Tag'),
 ('wms.opening.location','库位','Location'),
 ('wms.opening.quantity','数量','Quantity'),
 ('wms.opening.uom','库存单位','Inventory UOM'),
 ('wms.opening.quality','质量状态','Quality Status'),
 ('wms.opening.warehousestatus','仓储状态','Warehouse Status'),
 ('wms.opening.selectmaterial','选择有效物料','Select an active material'),
 ('wms.opening.selectlocation','选择有效库位','Select an active location'),
 ('wms.opening.addline','新增明细','Add Line'),
 ('wms.opening.linesaved','明细已保存','Line saved.'),
 ('wms.opening.deleteconfirm','确定删除此期初明细？','Delete this opening line?'),
 ('wms.opening.back','返回建账单','Back to Documents'),
 ('wms.opening.confirmpost','确认并过账','Confirm and Post'),
 ('wms.opening.postedresult','过账成功，交易编号','Posted successfully. Transaction ID'),
 ('wms.opening.postfailed','过账失败，请刷新核对结果后再操作','Posting failed. Refresh and verify the result before taking further action.'),
 ('wms.opening.activate','启用仓库库存','Activate Warehouse Inventory'),
 ('wms.opening.activated','仓库库存已启用','Warehouse inventory is active.'),
 ('wms.opening.activatefailed','仓库尚未核对或已启用','Warehouse is not awaiting activation or is already active.'),
 ('wms.opening.verifyinventory','核对实时库存','Verify Live Inventory'),
 ('wms.opening.lines','库存明细','Inventory Lines'),
 ('wms.opening.initializing','初始化中','Initializing'),
 ('wms.opening.pendingactivation','已过账待启用','Posted, Awaiting Activation'),
 ('wms.opening.active','已启用','Active'),
 ('wms.opening.materialsearch','物料编码或名称','Material code or name'),
 ('wms.opening.locationsearch','筛选本仓库库位','Filter locations in this warehouse'),
 ('wms.opening.activationconfirm','确认启用仓库库存','Confirm Warehouse Inventory Activation'),
 ('wms.opening.activationhint','请确认已完成期初账目与实时库存核对。启用后该仓库不能再次通过普通期初单增加初始库存。','Confirm that opening balances match live inventory. After activation, another regular opening document cannot add initial stock.'),
 ('wms.opening.deletedocumentconfirm','确定删除此未过账期初草稿及其明细？','Delete this unposted opening draft and all its lines?'),
 ('wms.error.opening_conflict','期初单状态或库存唯一性发生冲突，请刷新后核对','Opening document state or stock uniqueness conflict. Refresh and verify.')
), inserted AS (
 INSERT INTO wms_i18n_message_definition(id,message_key,enabled,version,deleted,created_by,created_at,updated_by,updated_at)
 SELECT '8000000000000000'||lpad((500+row_number() OVER(ORDER BY message_key))::text,3,'0'), message_key,true,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now()
 FROM messages RETURNING id,message_key
)
INSERT INTO wms_i18n_translation(id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at)
SELECT '810000000000000'||lpad((2000+row_number() OVER(ORDER BY inserted.message_key, locale.locale_code))::text,4,'0'), inserted.id, locale.locale_code, locale.message_text,0,false,'WMS_MIGRATION',now(),'WMS_MIGRATION',now()
FROM inserted JOIN messages USING(message_key)
CROSS JOIN LATERAL (VALUES ('zh-CN',messages.zh_text),('en-US',messages.en_text)) locale(locale_code,message_text);
