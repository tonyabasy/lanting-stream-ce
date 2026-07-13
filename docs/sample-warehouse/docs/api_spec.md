# API Specification

## Data Service API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/ads/order-report` | GET | 订单报表查询 |
| `/api/ads/user-retention` | GET | 用户留存查询 |
| `/api/ads/order-report/detail` | GET | 订单明细下钻 |

## 参数

### GET /api/ads/order-report

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| startDate | string | Y | 开始日期 yyyy-MM-dd |
| endDate | string | Y | 结束日期 |
| categoryId | int | N | 品类筛选 |

## 响应格式

```json
{
    "code": 0,
    "data": { ... },
    "message": "success"
}
```
