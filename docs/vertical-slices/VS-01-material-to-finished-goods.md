# VS-01：原料到成品自动入库

```text
供应商送货通知
→ WMS 创建收货任务
→ PDA/受控接口完成实物收货
→ WMS 创建批次、容器和 INSPECTION_HOLD 库存事实
→ MaterialReceived
→ QMS 来料检验与质量处置
→ InspectionDispositionDecided
→ WMS 调整 AVAILABLE / REJECTED / CONCESSION / DOWNGRADED
→ 生产工单
→ 原料预占与领用
→ PCS 模拟投料、混合、灌装
→ 半成品/成品批次
→ 成品检验放行
→ WCS 自动入库
→ 正反向批次追溯
```

## 验收重点

- WMS 拥有库存流水、余额、容器和库存质量状态；QMS 拥有检验任务、结果和处置结论，禁止跨库修改。
- 送货通知不等于实物库存；未检验或未放行库存可以存在但不能作为可用库存。
- 库存事实流水、实时余额与预占一致。
- 重复请求、重复消息和重复设备回执不产生重复业务事实。
- 批次谱系支持拆分、合并、返工、降级和召回影响分析。
- HTTP、消息和设备命令均可追踪。
- Gateway 多实例共享 Redis 限流。
