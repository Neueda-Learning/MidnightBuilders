# Frontend

第一轮迭代的零构建前端，使用原生 HTML、CSS 和 JavaScript。

## 目录

- `index.html`：页面语义结构
- `css/styles.css`：HSBC 品牌视觉、响应式布局与微动画
- `js/api.js`：REST API 访问层
- `js/app.js`：页面状态、交互与数据渲染

静态资源随 Spring Boot 一起发布，因此与 `/api/payments` 同源，无需额外配置 CORS。启动后端后访问 `http://localhost:8080/` 即可。
