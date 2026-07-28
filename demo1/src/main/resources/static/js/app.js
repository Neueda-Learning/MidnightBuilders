import { paymentApi } from "./api.js";

const state = { payments: [], selectedId: null };
const statusLabels = { CREATED: "待处理", VALIDATED: "已校验", SENT: "已发送", COMPLETED: "已完成", FAILED: "失败" };
const currencyLocales = { CNY: "zh-CN", USD: "en-US", GBP: "en-GB", EUR: "de-DE" };

const elements = {
    tableBody: document.querySelector("#paymentTableBody"),
    statusFilter: document.querySelector("#statusFilter"),
    search: document.querySelector("#paymentSearch"),
    empty: document.querySelector("#emptyState"),
    resultCount: document.querySelector("#resultCount"),
    modal: document.querySelector("#createModal"),
    form: document.querySelector("#createPaymentForm"),
    drawer: document.querySelector("#detailDrawer"),
    drawerBackdrop: document.querySelector("#drawerBackdrop"),
    detailContent: document.querySelector("#detailContent"),
    toastRegion: document.querySelector("#toastRegion")
};

function escapeHtml(value = "") {
    return String(value).replace(/[&<>'"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[char]);
}

function formatMoney(amount, currency = "CNY") {
    return new Intl.NumberFormat(currencyLocales[currency] || "zh-CN", { style: "currency", currency }).format(Number(amount || 0));
}

function formatDate(value) {
    if (!value) return "—";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }).format(date);
}

function toast(message, type = "success") {
    const node = document.createElement("div");
    node.className = `toast ${type}`;
    node.textContent = message;
    elements.toastRegion.append(node);
    window.setTimeout(() => node.remove(), 3600);
}

function renderMetrics() {
    const total = state.payments.reduce((sum, item) => sum + Number(item.amount || 0), 0);
    const completed = state.payments.filter((item) => item.status === "COMPLETED").length;
    const pending = state.payments.filter((item) => ["CREATED", "VALIDATED", "SENT"].includes(item.status)).length;
    document.querySelector("#totalAmount").textContent = formatMoney(total, "CNY");
    document.querySelector("#totalCount").textContent = state.payments.length;
    document.querySelector("#successRate").textContent = state.payments.length ? `${Math.round((completed / state.payments.length) * 100)}%` : "0%";
    document.querySelector("#pendingCount").textContent = pending;
}

function renderPayments() {
    const term = elements.search.value.trim().toLowerCase();
    const filtered = state.payments.filter((payment) => payment.id.toLowerCase().includes(term));
    elements.tableBody.innerHTML = filtered.map((payment, index) => `
        <tr class="row-enter" style="animation-delay:${Math.min(index * 35, 210)}ms">
            <td><span class="payment-id">${escapeHtml(payment.id)}</span></td>
            <td class="time-cell">${escapeHtml(formatDate(payment.createdAt))}</td>
            <td class="amount-cell">${escapeHtml(formatMoney(payment.amount, payment.currency))}</td>
            <td><span class="status status-${escapeHtml(payment.status)}">${escapeHtml(statusLabels[payment.status] || payment.status)}</span></td>
            <td>${payment.errorCode ? `<span class="error-text">${escapeHtml(payment.errorCode)}</span>` : '<span class="muted">—</span>'}</td>
            <td><button class="row-action" type="button" data-payment-id="${escapeHtml(payment.id)}" aria-label="查看付款详情">›</button></td>
        </tr>`).join("");
    elements.empty.hidden = filtered.length > 0;
    elements.resultCount.textContent = `共 ${filtered.length} 笔付款`;
    renderMetrics();
}

async function loadPayments({ quiet = false } = {}) {
    try {
        state.payments = await paymentApi.list(elements.statusFilter.value);
        renderPayments();
        if (!quiet) toast("付款数据已更新");
    } catch (error) {
        state.payments = [];
        renderPayments();
        toast(`${error.code ? `${error.code}：` : ""}${error.message}`, "error");
    }
}

function newIdempotencyKey() {
    return `payment-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function openCreate() {
    elements.form.reset();
    elements.form.elements.idempotencyKey.value = newIdempotencyKey();
    elements.modal.hidden = false;
    document.body.style.overflow = "hidden";
    window.setTimeout(() => elements.form.elements.sourceAccount.focus(), 50);
}

function closeCreate() {
    elements.modal.hidden = true;
    document.body.style.overflow = "";
}

function validateForm() {
    let valid = true;
    elements.form.querySelectorAll(".field-error").forEach((node) => { node.textContent = ""; });
    elements.form.querySelectorAll(".invalid").forEach((node) => node.classList.remove("invalid"));
    const source = elements.form.elements.sourceAccount;
    const destination = elements.form.elements.destinationAccount;
    const amount = elements.form.elements.amount;
    const setError = (input, message) => { input.classList.add("invalid"); input.closest("label").querySelector(".field-error").textContent = message; valid = false; };
    if (!source.value.trim()) setError(source, "请输入付款账户");
    if (!destination.value.trim()) setError(destination, "请输入收款账户");
    if (source.value.trim() && source.value.trim() === destination.value.trim()) setError(destination, "付款账户与收款账户不能相同");
    if (!amount.value || Number(amount.value) <= 0 || Number(amount.value) > 1000000) setError(amount, "金额须在 0.01 至 1,000,000.00 之间");
    return valid;
}

async function submitPayment(event) {
    event.preventDefault();
    if (!validateForm()) return;
    const submit = elements.form.querySelector('[type="submit"]');
    submit.disabled = true;
    submit.querySelector(".button-text").hidden = true;
    submit.querySelector(".spinner").hidden = false;
    const data = new FormData(elements.form);
    try {
        const payment = await paymentApi.create({
            sourceAccount: data.get("sourceAccount").trim(),
            destinationAccount: data.get("destinationAccount").trim(),
            amount: Number(data.get("amount")),
            currency: data.get("currency"),
            reference: data.get("reference").trim() || null
        }, data.get("idempotencyKey").trim());
        closeCreate();
        await loadPayments({ quiet: true });
        toast(`付款 ${payment.id} 已创建`);
        await openDetail(payment.id);
    } catch (error) {
        toast(`${error.code ? `${error.code}：` : ""}${error.message}`, "error");
    } finally {
        submit.disabled = false;
        submit.querySelector(".button-text").hidden = false;
        submit.querySelector(".spinner").hidden = true;
    }
}

async function openDetail(id) {
    state.selectedId = id;
    elements.drawerBackdrop.hidden = false;
    elements.drawer.setAttribute("aria-hidden", "false");
    elements.detailContent.innerHTML = '<div class="empty-state"><span class="spinner"></span><p>正在加载付款详情…</p></div>';
    requestAnimationFrame(() => elements.drawer.classList.add("open"));
    try {
        const [payment, history] = await Promise.all([paymentApi.get(id), paymentApi.history(id)]);
        elements.detailContent.innerHTML = `
            <div class="detail-amount"><small>付款金额</small><strong>${escapeHtml(formatMoney(payment.amount, payment.currency))}</strong></div>
            <div class="detail-grid">
                <div class="detail-field"><small>当前状态</small><span class="status status-${escapeHtml(payment.status)}">${escapeHtml(statusLabels[payment.status] || payment.status)}</span></div>
                <div class="detail-field"><small>付款编号</small><strong>${escapeHtml(payment.id)}</strong></div>
                <div class="detail-field"><small>付款账户</small><strong>${escapeHtml(payment.sourceAccount)}</strong></div>
                <div class="detail-field"><small>收款账户</small><strong>${escapeHtml(payment.destinationAccount)}</strong></div>
                <div class="detail-field"><small>创建时间</small><strong>${escapeHtml(formatDate(payment.createdAt))}</strong></div>
                <div class="detail-field"><small>付款用途</small><strong>${escapeHtml(payment.reference || "—")}</strong></div>
            </div>
            ${payment.status === "FAILED" ? `<div class="error-banner"><strong>${escapeHtml(payment.errorCode || "PROCESSING_ERROR")}</strong><p>${escapeHtml(payment.errorMessage || "付款处理失败，请联系系统管理员。")}</p></div>` : ""}
            <h3 class="section-title">状态变化历史</h3>
            <ol class="timeline">${history.map((item) => `<li><span class="timeline-dot"></span><div class="timeline-content"><strong>${escapeHtml(statusLabels[item.toStatus] || item.toStatus)}</strong><p>${escapeHtml(item.notes || `${item.fromStatus || "付款"} → ${item.toStatus}`)} · ${escapeHtml(item.triggeredBy || "SYSTEM")}</p><time>${escapeHtml(formatDate(item.changedAt))}</time></div></li>`).join("") || "<li><div class='timeline-content'><p>暂无历史记录</p></div></li>"}</ol>
            ${payment.status === "CREATED" ? '<button class="button button-primary drawer-action" type="button" data-action="process-payment">处理此付款</button>' : ""}`;
    } catch (error) {
        elements.detailContent.innerHTML = `<div class="error-banner"><strong>${escapeHtml(error.code || "LOAD_ERROR")}</strong><p>${escapeHtml(error.message)}</p></div>`;
    }
}

function closeDetail() {
    elements.drawer.classList.remove("open");
    window.setTimeout(() => { elements.drawerBackdrop.hidden = true; elements.drawer.setAttribute("aria-hidden", "true"); }, 280);
}

async function processSelectedPayment() {
    const button = elements.detailContent.querySelector('[data-action="process-payment"]');
    button.disabled = true;
    button.textContent = "处理中…";
    try {
        const result = await paymentApi.process(state.selectedId);
        toast(result.message || "付款处理完成");
        await Promise.all([loadPayments({ quiet: true }), openDetail(state.selectedId)]);
    } catch (error) {
        toast(`${error.code ? `${error.code}：` : ""}${error.message}`, "error");
        button.disabled = false;
        button.textContent = "处理此付款";
    }
}

document.addEventListener("click", (event) => {
    const action = event.target.closest("[data-action]")?.dataset.action;
    if (action === "open-create") openCreate();
    if (action === "close-create") closeCreate();
    if (action === "close-detail") closeDetail();
    if (action === "refresh") loadPayments();
    if (action === "process-payment") processSelectedPayment();
    if (action === "toggle-menu") document.querySelector(".sidebar").classList.toggle("open");
    const paymentId = event.target.closest("[data-payment-id]")?.dataset.paymentId;
    if (paymentId) openDetail(paymentId);
});

elements.drawerBackdrop.addEventListener("click", closeDetail);
elements.modal.addEventListener("click", (event) => { if (event.target === elements.modal) closeCreate(); });
elements.form.addEventListener("submit", submitPayment);
elements.form.elements.reference.addEventListener("input", (event) => { event.target.closest("label").querySelector(".char-count").textContent = `${event.target.value.length} / 255`; });
elements.search.addEventListener("input", renderPayments);
elements.statusFilter.addEventListener("change", () => loadPayments({ quiet: true }));
document.addEventListener("keydown", (event) => { if (event.key === "Escape") { if (!elements.modal.hidden) closeCreate(); else closeDetail(); } });

loadPayments({ quiet: true });
