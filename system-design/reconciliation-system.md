## 已完成记录

### 1. TEG 逾期账单发送发票邮件 -- 定时任务
- **谁用的功能**:财管/销售用，通知客户还款
- **需求**:TEG需要根据出具的发票，通过邮件方式通知客户付款
- **解决的问题**:在邮件中可以详细的说明客户的欠款情况，希望客户按照发票上的的费用支付

1. 任务入参 applyDate 发票申请时间
2. 获取Mysql中配置的发票模版
3. 获取Mysql中ims_bill_range_config维护的OU, 渠道，结算组关系配置表
4. 查询Mysql中ims_bill_mdm_config维护的mdmId客户，取出该BG需要出账的客户信息（发票日/应收日，信用期，客户邮箱地址，是否自动逾期，发票收件人等），如果查不到客户，就直接返回null
5. 通过ouId,mdmId,applyDate查询发票申请表，如果查不到直接返回null结束任务
6. 得到发票申请记录List,通过得到的对象中invoiceFileId 从idc 中获取发票byte数组，再上传到Jarvis图片服务器，得到一个新的fileId
7. 给发票邮件内容模版赋值，并填充发票图片fileId到内容中
8. 任务执行结束后，生成状态为待发送的邮件，业务确认后可以发送给`4`中配置的发票收件人

### 2. TEG,PCG FIT 月对账单自动催逾期任务
**由于各BG逻辑类似这里以FIT为例:**
- **谁用的功能**:财管/应收账款
- **需求**:需要通知逾期客户尽快还款
- **解决的问题**:使用邮件的方式 督促客户还款，邮件附件中可以标明详细的应收情况

- 代码实现逻辑如下:
1. 任务入参财务对象月份 periodName,按该月出账
2. 删除历史 periodName 月份的出账数据，从新生成，从配置表ims_bill_mdm_config获取该BG下需要出账的客户
3. 从数据范围表ims_bill_range_config（维护OU，渠道，结算组关联信息）中获取ouIds，channelIds,settlementGroupIds
4. 遍历所有的mdmId，单独去生成账单,分别通过mdmId，ou,channelId,结算组查询应收，发票，回款
5. 以OU的维度统计账期下的应收
6. 生成附件（pdf,excel）并上传到Jarvis平台，将生成的fileId存入账单对象中
7. 同时将发票打包存入账单


### 3. 时点余额(各个BG应收余额对账单-销售的邮件，应收余额预警-GM的邮件)
- **谁用的功能**:财管、销售、财管负责人
- **需求**: 对账催收平台-账单需求
- **解决的问题**:提醒销售应收账款的对账/回款情况
- 
[reconciliation_receivables_info.sql](../sql/reconciliation_receivables_info.sql)

- **邮件中的列表内容都是来自reconciliation_receivables_info**

- 销售邮件，了解当前的逾期情况
![reconcile-receivable-email-sales.png](../Images/reconcile-receivable-email-sales.png)
- GM 邮件 了解团队催收进度
![reconcile-receivable-email-gm.png](../Images/reconcile-receivable-email-gm.png)


### 4. 差异调整预提--对用于应收金额的调整-海外环境（AP，NA,EU）
- **谁用的功能**:财管
- **需求**: 月结后，调整用户应收金额
- **解决的问题**:国内有冲销的功能，这是国外的功能去除冲销，直接使用正负调整金额

1. 用户通过批量导入excel附件或在系统上单笔入录uin信息来完成导入
2. 单笔入录时 UIN(业务客户id)必填，从应收表中查询，没有提示不存在该客户，找到就回写biz_customer_name,mdm_id,mdm_name,并填写OU，渠道，结算组，调整金额，账单月，合同等
3. 批量导入时，UIN,结算组，调整金额，线上线下必填

- **1.单笔录入校验规则**：
    - 线上线下必填，当为线上时，必须填写对账月份，当为线下是，必须填写合同号
    - 单笔入录OU名称,渠道名称 会查询最近一个月的应收表，如果查询不到，提示不存在，结算组需要通过当前账期和名称查询
    - ouName和渠道存在的情况下，通过OUId和channelID查询税率，不校验是否存在税率
![accrual-adjust-single-input.png](../Images/accrual-adjust-single-input.png)

- **2.批量导入校验规则**：
    - 线上线下必填，当为线上时，必须填写对账月份，当为线下是，必须填写合同号
    - 当附件中没有填写OU，渠道时，通过UIN查询最近一个月的应收，从应收中获取并赋值
    - 填写的ou和渠道，会从OU表和渠道表中校验是否存在，不存在给与提示（不会再从应收表中校验是否正确）
    - 当填写的渠道和OU不在同一个环境（同时查询三套环境）提示不存在该渠道或OU和渠道不在同一个环境下

![accrual-adjust-batch-input.png](../Images/accrual-adjust-batch-input.png)








































