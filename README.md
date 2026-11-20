# 投递手账 JobLedger

一个自托管、多用户的求职投递管理服务。后端 **Spring Boot 4.2 + MyBatis-Plus**，前端 **React + Ant Design**，使用 MyBatis-Plus 多租户拦截器按账号隔离数据。

> 项目正在搭建设中，各模块完成后会依次提交。

## 模块

| 模块 | 说明 |
| --- | --- |
| `jobtrack-common` | 通用返回结构、异常、租户上下文 |
| `jobtrack-system` | 用户、登录、JWT、租户拦截器、偏好设置 |
| `jobtrack-application` | 投递记录、时间线、回收站、简历库、统计、Excel |
| `jobtrack-web` | 启动入口，托管构建后的 React 前端 |

## 默认账号

- 用户名：`admin`
- 密码：`123456`
