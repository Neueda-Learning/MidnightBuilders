import { paymentApi } from "./api.js?v=20260728-3";

const UI_LOG_PREFIX = "[PaymentUI]";
const PAYMENT_FLOW = ["CREATED", "VALIDATED", "SENT", "COMPLETED"];
const STATUS_RULES = {
    CREATED: ["VALIDATED", "FAILED"],
    VALIDATED: ["SENT", "FAILED"],
    SENT: ["COMPLETED", "FAILED"],
    COMPLETED: [],
    FAILED: []
};
const STATUS_META = {
    CREATED: {
        label: "待处理",
        icon: "◎",
        summary: "付款已创建，等待校验和后续处理。",
        hint: "可以进入处理流，下一步将由后端执行校验。"
    },
    VALIDATED: {
        label: "已校验",
        icon: "✓",
        summary: "付款通过业务校验，允许进入发送阶段。",
        hint: "后端规则允许继续发送，或在异常时进入失败分支。"
    },
    SENT: {
        label: "已发送",
        icon: "⇢",
        summary: "付款已发送到下游系统，等待确认结果。",
        hint: "若确认成功将完成，否则会按规则转为失败。"
    },
    COMPLETED: {
        label: "已完成",
        icon: "★",
        summary: "付款处理成功完成，状态已进入终态。",
        hint: "终态不可再次处理，建议仅查看详情与审计轨迹。"
    },
    FAILED: {
        label: "失败",
        icon: "!",
        summary: "付款在校验、发送或确认阶段失败，状态已进入终态。",
        hint: "请结合错误码和审计历史分析失败原因。"
    }
};
const statusLabels = Object.fromEntries(Object.entries(STATUS_META).map(([status, meta]) => [status, meta.label]));
const currencyLocales = { CNY: "zh-CN", USD: "en-US", GBP: "en-GB", EUR: "de-DE" };
const failureMessages = {
    ACCOUNT_NOT_FOUND: "付款方账户不在系统账户库中",
    NETWORK_TIMEOUT: "网络延迟超过 10 秒，重试三次后仍未成功",
    NETWORK_ERROR: "付款网络暂时不可用",
    INVALID_AMOUNT: "付款金额不符合业务规则",
    INVALID_CURRENCY: "付款币种不符合业务规则",
    INVALID_ACCOUNT: "付款账户字段不符合业务规则",
    SAME_SOURCE_AND_DESTINATION: "付款账户与收款账户不能相同"
};
const state = {
    payments: [],
    selectedId: null,
    selectedPayment: null,
    selectedHistory: [],
    isProcessing: false,
    isDetailOpen: false
};

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
    toastRegion: document.querySelector("#toastRegion"),
    stateOverview: document.querySelector("#stateOverview"),
    lifecycleTrack: document.querySelector("#lifecycleTrack"),
    lifecycleCaption: document.querySelector("#lifecycleCaption"),
    failureRail: document.querySelector("#failureRail"),
    allowedTransitions: document.querySelector("#allowedTransitions"),
    terminalBadge: document.querySelector("#terminalBadge"),
    stateActions: document.querySelector("#stateActions"),
    stateRuleMatrix: document.querySelector("#stateRuleMatrix"),
    selectedPaymentPill: document.querySelector("#selectedPaymentPill"),
    refreshSelectedButton: document.querySelector("#refreshSelectedButton"),
    auditPreview: document.querySelector("#auditPreview"),
    auditSelectionLabel: document.querySelector("#auditSelectionLabel"),
    globalLifecycleTrack: document.querySelector("#globalLifecycleTrack"),
    flowStripHint: document.querySelector("#flowStripHint")
};

const submitButton = elements.form?.querySelector('[type="submit"]');
let currentIdempotencyKey = "";

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

function hasStateConsoleDom() {
    return Boolean(
        elements.stateOverview
        && elements.lifecycleTrack
        && elements.lifecycleCaption
        && elements.failureRail
        && elements.allowedTransitions
        && elements.terminalBadge
        && elements.stateActions
        && elements.stateRuleMatrix
        && elements.selectedPaymentPill
        && elements.refreshSelectedButton
        && elements.auditPreview
        && elements.auditSelectionLabel
    );
}

function isTerminalStatus(status) {
    return status === "COMPLETED" || status === "FAILED";
}

function getAllowedStatuses(status) {
    return STATUS_RULES[status] || [];
}

function getSelectedPaymentSummary() {
    const payment = state.selectedPayment;
    if (!payment) {
        return null;
    }

    const allowedNext = getAllowedStatuses(payment.status);
    const latestHistory = state.selectedHistory[0] || null;
    return {
        payment,
        allowedNext,
        latestHistory,
        isTerminal: isTerminalStatus(payment.status),
        canProcess: payment.status === "CREATED" && !state.isProcessing
    };
}

function renderEmptyCard(title, description) {
    return `
        <div class="empty-card">
            <div>
                <strong>${escapeHtml(title)}</strong>
                <p>${escapeHtml(description)}</p>
            </div>
        </div>`;
}

function renderRuleMatrix() {
    if (!elements.stateRuleMatrix) {
        return;
    }
    elements.stateRuleMatrix.innerHTML = `
        <div class="matrix-list">
            ${Object.entries(STATUS_RULES).map(([status, nextStatuses]) => `
                <article class="matrix-row">
                    <div>
                        <strong>${escapeHtml(statusLabels[status] || status)}</strong>
                        <p>${escapeHtml(STATUS_META[status]?.summary || "")}</p>
                    </div>
                    <div class="transition-status-group">
                        ${nextStatuses.length
                            ? nextStatuses.map((nextStatus) => `<span class="mini-status ${escapeHtml(nextStatus)}">${escapeHtml(statusLabels[nextStatus] || nextStatus)}</span>`).join("")
                            : '<span class="mini-status COMPLETED">终态</span>'}
                    </div>
                </article>`).join("")}
        </div>`;
}

function renderStatusOverview() {
    if (!elements.stateOverview || !elements.selectedPaymentPill || !elements.refreshSelectedButton) {
        return;
    }
    const summary = getSelectedPaymentSummary();
    if (!summary) {
        elements.stateOverview.innerHTML = renderEmptyCard("先选择一笔付款", "从列表中点击任意付款，右侧会同步展示状态、合法下一状态、处理动作和审计轨迹。");
        elements.selectedPaymentPill.textContent = "未选择付款";
        elements.refreshSelectedButton.disabled = true;
        return;
    }

    const { payment, allowedNext, latestHistory, isTerminal } = summary;
    const statusMeta = STATUS_META[payment.status] || STATUS_META.CREATED;
    const bannerClass = payment.status === "FAILED" ? "state-summary-banner is-failed" : isTerminal ? "state-summary-banner is-terminal" : "state-summary-banner";

    elements.selectedPaymentPill.textContent = `${payment.id.slice(0, 8)} · ${statusMeta.label}`;
    elements.refreshSelectedButton.disabled = false;
    elements.stateOverview.innerHTML = `
        <div class="overview-header">
            <div>
                <p class="eyebrow">SELECTED PAYMENT</p>
                <h3>${escapeHtml(payment.id)}</h3>
                <p>与后端状态机联动的前端控制视图，帮助运营理解当前阶段与允许动作。</p>
            </div>
            <span class="status status-${escapeHtml(payment.status)}">${escapeHtml(statusMeta.label)}</span>
        </div>
        <div class="overview-current">
            <span class="status-orb ${escapeHtml(payment.status)}">${escapeHtml(statusMeta.icon)}</span>
            <div>
                <strong>${escapeHtml(statusMeta.label)}</strong>
                <span>${escapeHtml(statusMeta.summary)}</span>
            </div>
        </div>
        <div class="overview-meta">
            <article class="overview-meta-card"><small>付款金额</small><strong>${escapeHtml(formatMoney(payment.amount, payment.currency))}</strong></article>
            <article class="overview-meta-card"><small>允许下一状态</small><strong>${escapeHtml(allowedNext.map((status) => statusLabels[status] || status).join(" / ") || "无")}</strong></article>
            <article class="overview-meta-card"><small>最近变更</small><strong>${escapeHtml(latestHistory ? formatDate(latestHistory.changedAt) : formatDate(payment.updatedAt || payment.createdAt))}</strong></article>
            <article class="overview-meta-card"><small>错误信息</small><strong>${escapeHtml(payment.errorCode || "—")}</strong></article>
        </div>
        <div class="${bannerClass}">
            ${escapeHtml(payment.status === "FAILED"
                ? `${payment.errorCode || "PROCESSING_ERROR"}：${payment.errorMessage || statusMeta.hint}`
                : isTerminal
                    ? "付款已到达终态，前端将禁用进一步处理动作，只保留查看与刷新。"
                    : statusMeta.hint)}
        </div>`;
}

function renderLifecycleVisualization() {
    if (!elements.lifecycleTrack || !elements.lifecycleCaption || !elements.failureRail) {
        return;
    }
    const payment = state.selectedPayment;
    const currentStatus = payment?.status || null;
    const currentIndex = PAYMENT_FLOW.indexOf(currentStatus);
    const failureReached = currentStatus === "FAILED";

    elements.lifecycleCaption.textContent = payment
        ? `当前付款处于 ${statusLabels[currentStatus] || currentStatus}，以下视图与后端状态机规则实时对齐。`
        : "选择一笔付款后查看状态演进、合法下一状态与控制动作。";

    elements.lifecycleTrack.innerHTML = PAYMENT_FLOW.map((status, index) => {
        const meta = STATUS_META[status];
        const isCompletedStep = currentIndex > index;
        const isActiveStep = currentStatus === status;
        const isPending = currentStatus == null || currentIndex < index || failureReached;
        const classes = ["lifecycle-node"];
        if (failureReached && index <= Math.max(0, state.selectedHistory.findIndex((item) => item.toStatus === "FAILED"))) {
            classes.push(index < Math.max(currentIndex, 0) ? "completed-step" : "pending");
        } else if (isCompletedStep) {
            classes.push("completed-step");
        } else if (isActiveStep) {
            classes.push("active-step");
        } else if (isPending) {
            classes.push("pending");
        }
        if (failureReached && index === Math.max(currentIndex, 0)) {
            classes.push("failed-step");
        }

        return `
            <li class="${classes.join(" ")}" data-status="${escapeHtml(status)}">
                <span class="lifecycle-chip">${String(index + 1).padStart(2, "0")}</span>
                <span class="lifecycle-core">${escapeHtml(meta.icon)}</span>
                <strong>${escapeHtml(meta.label)}</strong>
                <small>${escapeHtml(status)}</small>
                <p class="lifecycle-hint">${escapeHtml(meta.summary)}</p>
            </li>`;
    }).join("");

    elements.failureRail.innerHTML = payment
        ? `<span class="mini-status FAILED">失败分支</span><span><strong>规则说明：</strong>${escapeHtml(currentStatus === "FAILED"
            ? `当前付款已进入失败终态${payment.errorCode ? `，错误码 ${payment.errorCode}` : ""}。`
            : "在 CREATED、VALIDATED、SENT 任一阶段都允许转入 FAILED，前端以审计轨迹说明失败发生点。")}</span>`
        : `<span class="mini-status FAILED">失败分支</span><span>选择一笔付款后可查看它是否命中过失败路径，以及失败发生在哪个阶段。</span>`;

    elements.globalLifecycleTrack?.querySelectorAll("li").forEach((node) => {
        node.classList.toggle("is-active", Boolean(currentStatus) && node.dataset.status === currentStatus);
    });

    if (elements.flowStripHint) {
        elements.flowStripHint.textContent = payment
            ? `当前关注付款：${payment.id} · ${statusLabels[currentStatus] || currentStatus}。`
            : "后端状态机定义了合法流转路径，前端仅展示并驱动这些规则允许的动作。";
    }
}

function renderAllowedTransitions() {
    if (!elements.allowedTransitions || !elements.terminalBadge) {
        return;
    }
    const summary = getSelectedPaymentSummary();
    if (!summary) {
        elements.allowedTransitions.innerHTML = renderEmptyCard("暂无状态上下文", "选中付款后，这里会展示当前状态允许流转到的下一状态集合。");
        elements.terminalBadge.textContent = "等待选择";
        elements.terminalBadge.classList.remove("is-terminal");
        return;
    }

    const { payment, allowedNext, isTerminal } = summary;
    elements.terminalBadge.textContent = isTerminal ? "当前为终态" : "当前可继续流转";
    elements.terminalBadge.classList.toggle("is-terminal", isTerminal);

    elements.allowedTransitions.innerHTML = allowedNext.length
        ? `<div class="transition-badges">
            <article class="transition-badge-card">
                <div>
                    <strong>${escapeHtml(statusLabels[payment.status] || payment.status)} 可流转到</strong>
                    <p>这里展示的是后端 PaymentStateMachine#getAllowedNextStatuses 在前端的镜像规则。</p>
                </div>
                <div class="transition-status-group">
                    ${allowedNext.map((status) => `<span class="mini-status ${escapeHtml(status)}">${escapeHtml(statusLabels[status] || status)}</span>`).join("")}
                </div>
            </article>
        </div>`
        : renderEmptyCard("没有下一状态", "当前付款已经处于终态，后端规则不允许再继续推进状态。\n");
}

function renderStateActions() {
    if (!elements.stateActions) {
        return;
    }
    const summary = getSelectedPaymentSummary();
    if (!summary) {
        elements.stateActions.innerHTML = renderEmptyCard("暂无可执行动作", "选择付款后，这里会根据当前状态展示合法操作，例如处理付款或刷新状态。\n");
        return;
    }

    const { payment, canProcess, isTerminal } = summary;
    const processButtonClass = `button button-primary action-button${state.isProcessing ? " processing" : ""}`;

    const actionCards = [
        `<article class="action-card">
            <div class="action-copy">
                <strong>刷新当前付款</strong>
                <p>重新拉取付款详情与审计记录，确保前端展示与数据库一致。</p>
            </div>
            <button class="button button-ghost action-button secondary" type="button" data-action="refresh-selected" ${state.selectedId ? "" : "disabled"}>刷新详情</button>
        </article>`
    ];

    if (payment.status === "CREATED") {
        actionCards.unshift(`<article class="action-card">
            <div class="action-copy">
                <strong>推进状态机处理</strong>
                <p>调用后端 POST /api/payments/{id}/process，由后端依次执行校验、发送、确认与失败分支控制。</p>
            </div>
            <button class="${processButtonClass}" type="button" data-action="process-payment" ${canProcess ? "" : "disabled"}>${state.isProcessing ? "处理中…" : "处理此付款"}</button>
        </article>`);
    } else {
        actionCards.unshift(`<article class="action-card">
            <div class="action-copy">
                <strong>${escapeHtml(isTerminal ? "当前状态不可再推进" : "等待后端继续推进")}</strong>
                <p>${escapeHtml(isTerminal
                    ? "该付款已到达终态，前端仅保留查看与审计能力。"
                    : "当前付款不在 CREATED 状态，前端不会展示人工推进按钮，以免绕过后端规则。 ")}</p>
            </div>
            <button class="button button-ghost action-button secondary" type="button" disabled>${escapeHtml(isTerminal ? "终态只读" : "受后端规则控制")}</button>
        </article>`);
    }

    elements.stateActions.innerHTML = `<div class="action-list">${actionCards.join("")}</div>`;
}

function renderAuditPreview() {
    if (!elements.auditPreview || !elements.auditSelectionLabel) {
        return;
    }
    if (!state.selectedPayment) {
        elements.auditSelectionLabel.textContent = "未选择付款";
        elements.auditPreview.innerHTML = renderEmptyCard("等待付款选择", "审计预览会在你选中一笔付款后展示最近状态变更、触发方和错误信息。");
        return;
    }

    elements.auditSelectionLabel.textContent = `${state.selectedPayment.id.slice(0, 8)} · ${statusLabels[state.selectedPayment.status] || state.selectedPayment.status}`;
    const history = state.selectedHistory.slice(0, 6);
    elements.auditPreview.innerHTML = history.length
        ? `<div class="audit-preview-list">
            ${history.map((item) => `
                <article class="audit-item">
                    <div>
                        <strong>${escapeHtml(statusLabels[item.toStatus] || item.toStatus)}</strong>
                        <p>${escapeHtml(item.notes || `${item.fromStatus || "付款创建"} → ${item.toStatus}`)}</p>
                        <time>${escapeHtml(formatDate(item.changedAt))}</time>
                    </div>
                    <div>
                        <span class="audit-tag${item.errorCode ? " error" : ""}">${escapeHtml(item.triggeredBy || "SYSTEM")}</span>
                        ${item.errorCode ? `<span class="audit-tag error">${escapeHtml(item.errorCode)}</span>` : ""}
                    </div>
                </article>`).join("")}
        </div>`
        : renderEmptyCard("暂无审计历史", "当前付款还没有历史记录返回。\n");
}

function renderStateConsole() {
    if (!hasStateConsoleDom()) {
        return;
    }
    renderStatusOverview();
    renderLifecycleVisualization();
    renderAllowedTransitions();
    renderStateActions();
    renderAuditPreview();
}

function renderMetrics() {
    const total = state.payments.reduce((sum, item) => sum + Number(item.amount || 0), 0);
    const completed = state.payments.filter((item) => item.status === "COMPLETED").length;
    const pending = state.payments.filter((item) => ["CREATED", "VALIDATED", "SENT"].includes(item.status)).length;
    const totalAmountNode = document.querySelector("#totalAmount");
    const totalCountNode = document.querySelector("#totalCount");
    const successRateNode = document.querySelector("#successRate");
    const pendingCountNode = document.querySelector("#pendingCount");

    if (totalAmountNode) {
        totalAmountNode.textContent = formatMoney(total, "CNY");
    }
    if (totalCountNode) {
        totalCountNode.textContent = state.payments.length;
    }
    if (successRateNode) {
        successRateNode.textContent = state.payments.length ? `${Math.round((completed / state.payments.length) * 100)}%` : "0%";
    }
    if (pendingCountNode) {
        pendingCountNode.textContent = pending;
    }
}

function setFieldError(input, message) {
    input.classList.add("invalid");
    const errorNode = input.closest("label")?.querySelector(".field-error");
    if (errorNode) {
        errorNode.textContent = message;
    }
    console.warn(`${UI_LOG_PREFIX} Field validation failed`, {
        field: input?.name || input?.id || "unknown",
        message
    });
}

function maskAccount(value) {
    return value ? `***${String(value).slice(-4)}` : "<empty>";
}

function maskToken(value) {
    if (!value) {
        return "<empty>";
    }
    if (value.length <= 10) {
        return `${value.slice(0, 2)}***${value.slice(-2)}`;
    }
    return `${value.slice(0, 6)}***${value.slice(-4)}`;
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
    renderStateConsole();
}

async function loadPayments({ quiet = false } = {}) {
    console.info(`${UI_LOG_PREFIX} Loading payments`, {
        quiet,
        statusFilter: elements.statusFilter.value || "ALL"
    });
    try {
        state.payments = await paymentApi.list(elements.statusFilter.value);
        if (state.selectedId) {
            const matchedPayment = state.payments.find((payment) => payment.id === state.selectedId) || null;
            state.selectedPayment = matchedPayment;
            if (!matchedPayment) {
                state.selectedId = null;
                state.selectedHistory = [];
            }
        }
        renderPayments();
        console.info(`${UI_LOG_PREFIX} Payments loaded`, { count: state.payments.length });
        if (!quiet) toast("付款数据已更新");
    } catch (error) {
        state.payments = [];
        renderPayments();
        console.error(`${UI_LOG_PREFIX} Failed to load payments`, {
            code: error.code,
            status: error.status,
            message: error.message
        });
        toast(`${error.code ? `${error.code}：` : ""}${error.message}`, "error");
    }
}

function newIdempotencyKey() {
    if (window.crypto?.randomUUID) {
        return `payment-${window.crypto.randomUUID()}`;
    }
    return `payment-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function ensureIdempotencyKey() {
    const field = elements.form.elements.idempotencyKey;
    const normalized = (field?.value || currentIdempotencyKey || "").trim();
    if (normalized) {
        if (field) {
            field.value = normalized;
        }
        currentIdempotencyKey = normalized;
        return normalized;
    }
    const generated = newIdempotencyKey();
    if (field) {
        field.value = generated;
    }
    currentIdempotencyKey = generated;
    return generated;
}

function openCreate() {
    elements.form.reset();
    currentIdempotencyKey = newIdempotencyKey();
    if (elements.form.elements.idempotencyKey) {
        elements.form.elements.idempotencyKey.value = currentIdempotencyKey;
    }
    elements.form.elements.reference.closest("label").querySelector(".char-count").textContent = "0 / 255";
    elements.modal.classList.remove("is-open");
    elements.modal.hidden = false;
    elements.modal.classList.add("is-open");
    elements.modal.setAttribute("aria-hidden", "false");
    document.body.style.overflow = "hidden";
    console.info(`${UI_LOG_PREFIX} Create payment modal opened`, {
        generatedIdempotencyKey: maskToken(currentIdempotencyKey)
    });
    window.setTimeout(() => elements.form.elements.sourceAccount.focus(), 50);
}

function closeCreate() {
    elements.modal.classList.remove("is-open");
    elements.modal.setAttribute("aria-hidden", "true");
    document.body.style.overflow = "";
    window.setTimeout(() => {
        elements.modal.hidden = true;
    }, 180);
    console.info(`${UI_LOG_PREFIX} Create payment modal closed`);
}

function logSubmitButtonClick() {
    console.info(`${UI_LOG_PREFIX} Submit button clicked`, {
        disabled: submitButton?.disabled,
        modalHidden: elements.modal.hidden
    });
}

function validateForm() {
    let valid = true;
    console.info(`${UI_LOG_PREFIX} Validating payment form`);
    elements.form.querySelectorAll(".field-error").forEach((node) => { node.textContent = ""; });
    elements.form.querySelectorAll(".invalid").forEach((node) => node.classList.remove("invalid"));
    const source = elements.form.elements.sourceAccount;
    const destination = elements.form.elements.destinationAccount;
    const amount = elements.form.elements.amount;
    const setError = (input, message) => { setFieldError(input, message); valid = false; };
    if (!source.value.trim()) setError(source, "请输入付款账户");
    if (!destination.value.trim()) setError(destination, "请输入收款账户");
    if (source.value.trim() && source.value.trim() === destination.value.trim()) setError(destination, "付款账户与收款账户不能相同");
    if (!amount.value || Number(amount.value) <= 0 || Number(amount.value) > 1000000) setError(amount, "金额须在 0.01 至 1,000,000.00 之间");
    const idempotencyKey = ensureIdempotencyKey();
    console.info(`${UI_LOG_PREFIX} Payment form validation completed`, {
        valid,
        sourceAccount: maskAccount(source.value.trim()),
        destinationAccount: maskAccount(destination.value.trim()),
        amount: amount.value ? Number(amount.value) : null,
        currency: elements.form.elements.currency.value,
        idempotencyKey: maskToken(idempotencyKey)
    });
    return valid;
}

async function submitPayment(event) {
    event.preventDefault();
    console.info(`${UI_LOG_PREFIX} Submit payment triggered`);
    if (!validateForm()) {
        console.warn(`${UI_LOG_PREFIX} Submit payment aborted due to validation failure`);
        return;
    }
    const submit = elements.form.querySelector('[type="submit"]');
    submit.disabled = true;
    submit.querySelector(".button-text").hidden = true;
    submit.querySelector(".spinner").hidden = false;
    const data = new FormData(elements.form);
    const idempotencyKey = ensureIdempotencyKey();
    const requestSummary = {
        sourceAccount: maskAccount(data.get("sourceAccount")?.trim()),
        destinationAccount: maskAccount(data.get("destinationAccount")?.trim()),
        amount: Number(data.get("amount")),
        currency: data.get("currency"),
        referenceLength: (data.get("reference")?.trim() || "").length,
        idempotencyKey: maskToken(idempotencyKey)
    };
    console.info(`${UI_LOG_PREFIX} Payment submit request prepared`, requestSummary);
    try {
        const payment = await paymentApi.create({
            sourceAccount: data.get("sourceAccount").trim(),
            destinationAccount: data.get("destinationAccount").trim(),
            amount: Number(data.get("amount")),
            currency: data.get("currency"),
            reference: data.get("reference").trim() || null
        }, idempotencyKey);
        console.info(`${UI_LOG_PREFIX} Payment submit succeeded`, {
            paymentId: payment.id,
            status: payment.status,
            idempotencyKey: maskToken(idempotencyKey)
        });
        closeCreate();
        await loadPayments({ quiet: true });
        toast(`付款 ${payment.id} 已创建`);
        await openDetail(payment.id);
    } catch (error) {
        console.error(`${UI_LOG_PREFIX} Payment submit failed`, {
            code: error.code,
            status: error.status,
            message: error.message,
            idempotencyKey: maskToken(idempotencyKey)
        });
        toast(`${error.code ? `${error.code}：` : ""}${error.message}`, "error");
    } finally {
        submit.disabled = false;
        submit.querySelector(".button-text").hidden = false;
        submit.querySelector(".spinner").hidden = true;
        console.info(`${UI_LOG_PREFIX} Payment submit UI state restored`);
    }
}

async function openDetail(id) {
    state.selectedId = id;
    console.info(`${UI_LOG_PREFIX} Opening payment detail`, { paymentId: id });
    elements.drawerBackdrop.hidden = false;
    elements.drawer.setAttribute("aria-hidden", "false");
    elements.detailContent.innerHTML = '<div class="empty-state"><span class="spinner"></span><p>正在加载付款详情…</p></div>';
    requestAnimationFrame(() => elements.drawer.classList.add("open"));
    state.isDetailOpen = true;
    try {
        const [payment, history] = await Promise.all([paymentApi.get(id), paymentApi.history(id)]);
        state.selectedPayment = payment;
        state.selectedHistory = Array.isArray(history) ? history : [];
        console.info(`${UI_LOG_PREFIX} Payment detail loaded`, {
            paymentId: id,
            status: payment.status,
            historyCount: history.length
        });
        renderStateConsole();
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
        console.error(`${UI_LOG_PREFIX} Failed to load payment detail`, {
            paymentId: id,
            code: error.code,
            status: error.status,
            message: error.message
        });
        elements.detailContent.innerHTML = `<div class="error-banner"><strong>${escapeHtml(error.code || "LOAD_ERROR")}</strong><p>${escapeHtml(error.message)}</p></div>`;
    }
}

function closeDetail() {
    elements.drawer.classList.remove("open");
    state.isDetailOpen = false;
    window.setTimeout(() => { elements.drawerBackdrop.hidden = true; elements.drawer.setAttribute("aria-hidden", "true"); }, 280);
}

async function processSelectedPayment() {
    const button = elements.detailContent.querySelector('[data-action="process-payment"]');
    console.info(`${UI_LOG_PREFIX} Process payment triggered`, { paymentId: state.selectedId });
    state.isProcessing = true;
    renderStateConsole();
    if (button) {
        button.disabled = true;
        button.textContent = "处理中…";
    }
    try {
        const result = await paymentApi.process(state.selectedId);
        console.info(`${UI_LOG_PREFIX} Process payment succeeded`, {
            paymentId: state.selectedId,
            currentStatus: result.currentStatus,
            previousStatus: result.previousStatus
        });
        if (result.currentStatus === "FAILED") {
            const detail = failureMessages[result.errorCode] || result.errorMessage || "付款处理失败";
            const attempts = result.attemptCount ? `（共尝试 ${result.attemptCount} 次）` : "";
            toast(`${result.errorCode || "PROCESSING_ERROR"}：${detail}${attempts}`, "error");
        } else {
            toast(result.message || "付款处理完成");
        }
        await Promise.all([loadPayments({ quiet: true }), openDetail(state.selectedId)]);
    } catch (error) {
        console.error(`${UI_LOG_PREFIX} Process payment failed`, {
            paymentId: state.selectedId,
            code: error.code,
            status: error.status,
            message: error.message
        });
        toast(`${error.code ? `${error.code}：` : ""}${error.message}`, "error");
        if (button) {
            button.disabled = false;
            button.textContent = "处理此付款";
        }
    } finally {
        state.isProcessing = false;
        renderStateConsole();
    }
}

document.addEventListener("click", (event) => {
    const action = event.target.closest("[data-action]")?.dataset.action;
    if (action === "open-create") { event.preventDefault(); openCreate(); }
    if (action === "close-create") { event.preventDefault(); closeCreate(); }
    if (action === "close-detail") { event.preventDefault(); closeDetail(); }
    if (action === "refresh") loadPayments();
    if (action === "process-payment") processSelectedPayment();
    if (action === "refresh-selected" && state.selectedId) openDetail(state.selectedId);
    if (action === "toggle-menu") document.querySelector(".sidebar").classList.toggle("open");
    const paymentId = event.target.closest("[data-payment-id]")?.dataset.paymentId;
    if (paymentId) openDetail(paymentId);
});

window.addEventListener("error", (event) => {
    console.error(`${UI_LOG_PREFIX} Unhandled browser error`, {
        message: event.message,
        source: event.filename,
        line: event.lineno,
        column: event.colno
    });
});

window.addEventListener("unhandledrejection", (event) => {
    console.error(`${UI_LOG_PREFIX} Unhandled promise rejection`, {
        reason: event.reason?.message || String(event.reason)
    });
});

elements.drawerBackdrop.addEventListener("click", closeDetail);
elements.modal.classList.remove("is-open");
elements.modal.hidden = true;
elements.modal.setAttribute("aria-hidden", "true");
elements.modal.addEventListener("click", (event) => { if (event.target === elements.modal) closeCreate(); });
elements.form.addEventListener("submit", () => {
    console.info(`${UI_LOG_PREFIX} Native form submit event captured`, {
        idempotencyKey: maskToken(ensureIdempotencyKey())
    });
}, true);
elements.form.addEventListener("submit", submitPayment);
submitButton?.addEventListener("click", logSubmitButtonClick);
elements.form.elements.reference.addEventListener("input", (event) => { event.target.closest("label").querySelector(".char-count").textContent = `${event.target.value.length} / 255`; });
elements.search.addEventListener("input", renderPayments);
elements.statusFilter.addEventListener("change", () => loadPayments({ quiet: true }));
document.addEventListener("keydown", (event) => { if (event.key === "Escape") { if (!elements.modal.hidden) closeCreate(); else closeDetail(); } });

renderRuleMatrix();
renderStateConsole();
loadPayments({ quiet: true });
