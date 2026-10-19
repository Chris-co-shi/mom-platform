package io.github.chrisshi.mom.wms.infrastructure.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.chrisshi.mom.wms.application.QualityStatus;
import io.github.chrisshi.mom.wms.application.WarehouseStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * WMS 期初单及仓库库存生命周期的 PostgreSQL 持久化入口。
 * <p>仅供 WMS Application 在本地事务中使用；不访问或修改 MDM Schema。</p>
 */
@Mapper
public interface OpeningInventoryMapper {
    /** 期初单持久化读取视图，名称和编码为保存时 MDM 快照。 */
    /**
     * 期初单持久化读取视图；仓库状态来自 WMS 生命周期表。
     * @param id 技术主键 @param documentNo 期初单业务编号 @param warehouseId MDM 仓库稳定 ID
     * @param warehouseCode 创建时仓库编码快照 @param status 单据 DRAFT/POSTED 状态
     * @param transactionId 过账交易 ID @param createdBy 创建操作人 @param version 明细变更版本
     * @param warehouseState WMS 仓库库存启用状态
     */
    record Document(String id, String documentNo, String warehouseId, String warehouseCode, String status,
                    String transactionId, String createdBy, long version, String warehouseState) { }
    /**
     * 期初明细持久化视图，数量使用精确十进制。
     * @param id 明细技术主键 @param lineNo 单据内顺序号 @param materialId MDM 物料稳定 ID
     * @param materialCode 物料编码快照 @param materialName 物料名称快照 @param batchCode 真实批次身份
     * @param snCode 可选且全局唯一的 SN 标签 @param warehouseId MDM 仓库稳定 ID
     * @param warehouseCode 仓库编码快照 @param locationId MDM 库位稳定 ID @param locationCode 库位编码快照
     * @param qualityStatus 质量状态 @param warehouseStatus 仓储状态 @param quantity 正数库存数量 @param uomCode 库存计量单位快照
     */
    record Line(String id, int lineNo, String materialId, String materialCode, String materialName,
                String batchCode, String snCode, String warehouseId, String warehouseCode,
                String locationId, String locationCode, QualityStatus qualityStatus,
                WarehouseStatus warehouseStatus, BigDecimal quantity, String uomCode) { }

    /**
     * 初始化仓库库存生命周期；唯一仓库约束使并发创建只有一条状态记录。
     * @param id 状态技术 ID @param warehouseId MDM 仓库 ID @param warehouseCode 仓库编码快照 @return 插入行数，已存在时为零
     */
    @Insert("INSERT INTO wms_warehouse_inventory_state(id,warehouse_id,warehouse_code,state) VALUES(#{id},#{warehouseId},#{warehouseCode},'INITIALIZING') ON CONFLICT(warehouse_id) DO NOTHING")
    int initialize(@Param("id") String id, @Param("warehouseId") String warehouseId, @Param("warehouseCode") String warehouseCode);
    /**
     * 锁定仓库库存状态行以串行化建账、过账和启用。
     * @param warehouseId MDM 仓库 ID @return 当前 WMS 仓库库存状态
     */
    @Select("SELECT state FROM wms_warehouse_inventory_state WHERE warehouse_id=#{warehouseId} FOR UPDATE")
    String lockWarehouse(@Param("warehouseId") String warehouseId);
    /**
     * 插入草稿单及其创建责任主体。
     * @param id 技术 ID @param documentNo 业务编号 @param warehouseId MDM 仓库 ID
     * @param warehouseCode 仓库编码快照 @param actor 创建责任主体 @return 插入行数
     */
    @Insert("INSERT INTO wms_opening_document(id,document_no,warehouse_id,warehouse_code,status,created_by) VALUES(#{id},#{documentNo},#{warehouseId},#{warehouseCode},'DRAFT',#{actor})")
    int createDocument(@Param("id") String id, @Param("documentNo") String documentNo,
                       @Param("warehouseId") String warehouseId, @Param("warehouseCode") String warehouseCode,
                       @Param("actor") String actor);
    /**
     * 读取单据及其 WMS 仓库生命周期状态。
     * @param id 期初单 ID @return 单据视图，不存在时为空
     */
    @Select("SELECT d.id,d.document_no AS documentNo,d.warehouse_id AS warehouseId,d.warehouse_code AS warehouseCode,d.status,d.transaction_id AS transactionId,d.created_by AS createdBy,d.version,s.state AS warehouseState FROM wms_opening_document d JOIN wms_warehouse_inventory_state s ON s.warehouse_id=d.warehouse_id WHERE d.id=#{id}")
    Document find(@Param("id") String id);
    /**
     * 查询期初建账单列表，最近创建的单据优先。
     * @return 期初单视图列表
     */
    @Select("SELECT d.id,d.document_no AS documentNo,d.warehouse_id AS warehouseId,d.warehouse_code AS warehouseCode,d.status,d.transaction_id AS transactionId,d.created_by AS createdBy,d.version,s.state AS warehouseState FROM wms_opening_document d JOIN wms_warehouse_inventory_state s ON s.warehouse_id=d.warehouse_id ORDER BY d.created_at DESC,d.id DESC")
    IPage<Document> list(IPage<Document> page);
    /**
     * 锁定期初单主行以串行化草稿变更和过账。
     * @param id 期初单 ID @return 锁定的单据视图，不存在时为空
     */
    @Select("SELECT d.id,d.document_no AS documentNo,d.warehouse_id AS warehouseId,d.warehouse_code AS warehouseCode,d.status,d.transaction_id AS transactionId,d.created_by AS createdBy,d.version,s.state AS warehouseState FROM wms_opening_document d JOIN wms_warehouse_inventory_state s ON s.warehouse_id=d.warehouse_id WHERE d.id=#{id} FOR UPDATE OF d")
    Document lockDocument(@Param("id") String id);
    /**
     * 按单据顺序读取所有明细，供展示和单笔过账使用。
     * @param documentId 期初单 ID @return 明细列表
     */
    @Select("SELECT id,line_no AS lineNo,material_id AS materialId,material_code AS materialCode,material_name AS materialName,batch_code AS batchCode,sn_code AS snCode,warehouse_id AS warehouseId,warehouse_code AS warehouseCode,location_id AS locationId,location_code AS locationCode,quality_status AS qualityStatus,warehouse_status AS warehouseStatus,quantity,uom_code AS uomCode FROM wms_opening_document_line WHERE document_id=#{documentId} ORDER BY line_no")
    List<Line> lines(@Param("documentId") String documentId);
    /**
     * 在已锁定单据下分配下一个明细顺序号。
     * @param documentId 期初单 ID @return 下一顺序号
     */
    @Select("SELECT COALESCE(MAX(line_no),0)+1 FROM wms_opening_document_line WHERE document_id=#{documentId}")
    int nextLineNo(@Param("documentId") String documentId);
    /**
     * 插入已由 MDM 核验的库存维度和名称快照。
     * @param id 明细 ID @param documentId 单据 ID @param lineNo 单据内顺序号 @param materialId 物料 ID
     * @param materialCode 物料编码 @param materialName 物料名称快照 @param batchCode 真实批次身份
     * @param snCode 可空 SN 标签 @param warehouseId 仓库 ID @param warehouseCode 仓库编码快照
     * @param locationId 库位 ID @param locationCode 库位编码快照 @param qualityStatus 质量状态
     * @param warehouseStatus 仓储状态 @param quantity 正数数量 @param uomCode 库存计量单位 @return 插入行数
     */
    @Insert("INSERT INTO wms_opening_document_line(id,document_id,line_no,material_id,material_code,material_name,batch_code,sn_code,warehouse_id,warehouse_code,location_id,location_code,quality_status,warehouse_status,quantity,uom_code) VALUES(#{id},#{documentId},#{lineNo},#{materialId},#{materialCode},#{materialName},#{batchCode},#{snCode},#{warehouseId},#{warehouseCode},#{locationId},#{locationCode},#{qualityStatus},#{warehouseStatus},#{quantity},#{uomCode})")
    int insertLine(@Param("id") String id, @Param("documentId") String documentId, @Param("lineNo") int lineNo,
                   @Param("materialId") String materialId, @Param("materialCode") String materialCode,
                   @Param("materialName") String materialName, @Param("batchCode") String batchCode,
                   @Param("snCode") String snCode, @Param("warehouseId") String warehouseId,
                   @Param("warehouseCode") String warehouseCode, @Param("locationId") String locationId,
                   @Param("locationCode") String locationCode, @Param("qualityStatus") QualityStatus qualityStatus,
                   @Param("warehouseStatus") WarehouseStatus warehouseStatus, @Param("quantity") BigDecimal quantity,
                   @Param("uomCode") String uomCode);
    /**
     * 删除指定草稿行；业务状态由调用方锁定校验。
     * @param documentId 单据 ID @param lineId 明细 ID @return 删除行数
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM wms_opening_document_line WHERE id=#{lineId} AND document_id=#{documentId}")
    int deleteLine(@Param("documentId") String documentId, @Param("lineId") String lineId);
    /**
     * 仅更新目标草稿行的业务内容与当前 MDM 快照。
     * @param documentId 单据 ID @param lineId 明细 ID @param materialId 物料 ID @param materialCode 物料编码
     * @param materialName 物料名称快照 @param batchCode 真实批次身份 @param snCode 可空 SN 标签
     * @param locationId 库位 ID @param locationCode 库位编码快照 @param qualityStatus 质量状态
     * @param warehouseStatus 仓储状态 @param quantity 正数数量 @param uomCode 库存计量单位 @return 更新行数
     */
    @Update("UPDATE wms_opening_document_line SET material_id=#{materialId},material_code=#{materialCode},material_name=#{materialName},batch_code=#{batchCode},sn_code=#{snCode},location_id=#{locationId},location_code=#{locationCode},quality_status=#{qualityStatus},warehouse_status=#{warehouseStatus},quantity=#{quantity},uom_code=#{uomCode} WHERE id=#{lineId} AND document_id=#{documentId}")
    int updateLine(@Param("documentId") String documentId, @Param("lineId") String lineId,
                   @Param("materialId") String materialId, @Param("materialCode") String materialCode,
                   @Param("materialName") String materialName, @Param("batchCode") String batchCode,
                   @Param("snCode") String snCode, @Param("locationId") String locationId,
                   @Param("locationCode") String locationCode, @Param("qualityStatus") QualityStatus qualityStatus,
                   @Param("warehouseStatus") WarehouseStatus warehouseStatus, @Param("quantity") BigDecimal quantity,
                   @Param("uomCode") String uomCode);
    /**
     * 明细成功变化后推进单据版本，使事务外远程校验可被 CAS 检查。
     * @param id 单据 ID @return 更新行数
     */
    @Update("UPDATE wms_opening_document SET updated_at=now(),version=version+1 WHERE id=#{id} AND status='DRAFT'")
    int bumpDocumentVersion(@Param("id") String id);
    /**
     * 以版本条件将草稿变为已过账并关联唯一库存交易。
     * @param id 单据 ID @param transactionId 唯一库存交易 ID @param version 远程校验时的单据版本 @return 更新行数
     */
    @Update("UPDATE wms_opening_document SET status='POSTED',transaction_id=#{transactionId},updated_at=now(),version=version+1 WHERE id=#{id} AND status='DRAFT' AND version=#{version}")
    int postDocument(@Param("id") String id, @Param("transactionId") String transactionId, @Param("version") long version);
    /**
     * 删除草稿明细；仅由已锁定的草稿删除事务调用。
     * @param id 单据 ID @return 删除行数
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM wms_opening_document_line WHERE document_id=#{id}")
    int deleteLinesByDocument(@Param("id") String id);
    /**
     * 删除草稿单据；过账历史不得通过该入口删除。
     * @param id 单据 ID @return 删除行数
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM wms_opening_document WHERE id=#{id} AND status='DRAFT'")
    int deleteDraft(@Param("id") String id);
    /**
     * 删除不再被草稿占用的初始化中仓库状态。
     * @param warehouseId 仓库 ID @return 删除行数
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM wms_warehouse_inventory_state WHERE warehouse_id=#{warehouseId} AND state='INITIALIZING' AND opening_document_id IS NULL")
    int deleteInitializingWarehouse(@Param("warehouseId") String warehouseId);
    /**
     * 成功过账后将仓库置为待核对启用状态。
     * @param warehouseId 仓库 ID @param documentId 已过账单据 ID @return 更新行数
     */
    @Update("UPDATE wms_warehouse_inventory_state SET state='PENDING_ACTIVATION',opening_document_id=#{documentId},updated_at=now() WHERE warehouse_id=#{warehouseId} AND state='INITIALIZING'")
    int markPending(@Param("warehouseId") String warehouseId, @Param("documentId") String documentId);
    /**
     * 记录实际操作人并正式启用仓库库存。
     * @param warehouseId 仓库 ID @param actor 实际操作人 @return 更新行数
     */
    @Update("UPDATE wms_warehouse_inventory_state SET state='ACTIVE',activated_by=#{actor},activated_at=now(),updated_at=now() WHERE warehouse_id=#{warehouseId} AND state='PENDING_ACTIVATION'")
    int activate(@Param("warehouseId") String warehouseId, @Param("actor") String actor);
    /**
     * 读取仓库库存生命周期状态。
     * @param warehouseId 仓库 ID @return 当前状态，不存在时为空
     */
    @Select("SELECT state FROM wms_warehouse_inventory_state WHERE warehouse_id=#{warehouseId}")
    String warehouseState(@Param("warehouseId") String warehouseId);
    /** 检查期初之前该仓库是否已有实时余额，避免初始库存叠加。 */
    @Select("SELECT EXISTS(SELECT 1 FROM wms_inventory_balance WHERE warehouse_id=#{warehouseId})")
    boolean hasWarehouseBalance(@Param("warehouseId") String warehouseId);
}
