const messages = new Map([
    ["主导航", ["主导航", "Main navigation"]],
    ["切换语言", ["切换语言", "Switch language"]],
    ["Midnight Pay 企业付款处理工作台", ["Midnight Pay 企业付款处理工作台", "Midnight Pay corporate payment operations dashboard"]],
    ["PAYMENTS", ["支付", "PAYMENTS"]],
    ["Payment Ops Console", ["支付运营控制台", "Payment Ops Console"]],
    ["Version 1.0.0", ["版本 1.0.0", "Version 1.0.0"]],
    ["CREATED", ["已创建", "CREATED"]],
    ["VALIDATED", ["已校验", "VALIDATED"]],
    ["SENT", ["已发送", "SENT"]],
    ["COMPLETED", ["已完成", "COMPLETED"]],
    ["FAILED", ["失败", "FAILED"]],
    ["USER", ["用户", "USER"]],
    ["SYSTEM", ["系统", "SYSTEM"]],
    ["ACCOUNT_NOT_FOUND", ["付款方账户不存在", "ACCOUNT_NOT_FOUND"]],
    ["NETWORK_TIMEOUT", ["网络超时", "NETWORK_TIMEOUT"]],
    ["NETWORK_ERROR", ["网络错误", "NETWORK_ERROR"]],
    ["INVALID_AMOUNT", ["金额不合法", "INVALID_AMOUNT"]],
    ["INVALID_CURRENCY", ["币种不合法", "INVALID_CURRENCY"]],
    ["INVALID_ACCOUNT", ["账户不合法", "INVALID_ACCOUNT"]],
    ["SAME_SOURCE_AND_DESTINATION", ["付款与收款账户相同", "SAME_SOURCE_AND_DESTINATION"]],
    ["PROCESSING_ERROR", ["处理错误", "PROCESSING_ERROR"]],
    ["Midnight Pay 首页", ["Midnight Pay 首页", "Midnight Pay home"]],
    ["工作台", ["工作台", "Dashboard"]],
    ["付款管理", ["付款管理", "Payments"]],
    ["新建付款", ["新建付款", "New payment"]],
    ["审计记录", ["审计记录", "Audit records"]],
    ["UAT 环境", ["UAT 环境", "UAT environment"]],
    ["打开导航", ["打开导航", "Open navigation"]],
    ["企业支付", ["企业支付", "Corporate payments"]],
    ["刷新数据", ["刷新数据", "Refresh data"]],
    ["运营专员", ["运营专员", "Operations specialist"]],
    ["Payment Operations", ["支付运营", "Payment Operations"]],
    ["PAYMENT OPERATIONS", ["支付运营", "PAYMENT OPERATIONS"]],
    ["付款处理工作台", ["付款处理工作台", "Payment Operations Dashboard"]],
    ["集中管理付款指令、处理状态与完整审计轨迹。", ["集中管理付款指令、处理状态与完整审计轨迹。", "Manage payment instructions, processing states, and complete audit trails in one place."]],
    ["付款数据概览", ["付款数据概览", "Payment overview"]],
    ["本期付款总额", ["本期付款总额", "Total payment value"]],
    ["实时", ["实时", "Live"]],
    ["较上一周期", ["较上一周期", "vs. previous period"]],
    ["付款总笔数", ["付款总笔数", "Total payments"]],
    ["当前筛选范围", ["当前筛选范围", "Current filter"]],
    ["处理成功率", ["处理成功率", "Success rate"]],
    ["稳定", ["稳定", "Stable"]],
    ["运行表现良好", ["运行表现良好", "Operating normally"]],
    ["待处理指令", ["待处理指令", "Pending payments"]],
    ["需要运营人员关注", ["需要运营人员关注", "Requires attention"]],
    ["近期付款", ["近期付款", "Recent payments"]],
    ["查看并跟踪付款处理状态", ["查看并跟踪付款处理状态", "View and track payment status"]],
    ["搜索付款 ID", ["搜索付款 ID", "Search payment ID"]],
    ["按状态筛选", ["按状态筛选", "Filter by status"]],
    ["全部状态", ["全部状态", "All statuses"]],
    ["待处理", ["待处理", "Created"]],
    ["已校验", ["已校验", "Validated"]],
    ["已发送", ["已发送", "Sent"]],
    ["已完成", ["已完成", "Completed"]],
    ["失败", ["失败", "Failed"]],
    ["付款编号", ["付款编号", "Payment ID"]],
    ["创建时间", ["创建时间", "Created at"]],
    ["金额", ["金额", "Amount"]],
    ["状态", ["状态", "Status"]],
    ["异常信息", ["异常信息", "Error"]],
    ["操作", ["操作", "Action"]],
    ["暂无匹配付款", ["暂无匹配付款", "No matching payments"]],
    ["调整筛选条件，或新建一笔付款。", ["调整筛选条件，或新建一笔付款。", "Change the filters or create a new payment."]],
    ["数据按创建时间倒序展示", ["数据按创建时间倒序展示", "Sorted by newest first"]],
    ["状态管理控制台", ["状态管理控制台", "State Management Console"]],
    ["前端严格映射后端状态机规则，仅暴露合法操作并实时呈现状态演进。", ["前端严格映射后端状态机规则，仅暴露合法操作并实时呈现状态演进。", "The interface mirrors backend state-machine rules and exposes only valid actions."]],
    ["未选择付款", ["未选择付款", "No payment selected"]],
    ["刷新所选付款", ["刷新所选付款", "Refresh selected payment"]],
    ["STATE MACHINE", ["状态机", "STATE MACHINE"]],
    ["付款生命周期轨迹", ["付款生命周期轨迹", "Payment lifecycle"]],
    ["选择一笔付款后查看状态演进、合法下一状态与控制动作。", ["选择一笔付款后查看状态演进、合法下一状态与控制动作。", "Select a payment to view its lifecycle, valid next states, and actions."]],
    ["NEXT STATES", ["下一状态", "NEXT STATES"]],
    ["允许的下一状态", ["允许的下一状态", "Allowed next states"]],
    ["等待选择", ["等待选择", "Awaiting selection"]],
    ["CONTROLS", ["操作控制", "CONTROLS"]],
    ["状态控制动作", ["状态控制动作", "State actions"]],
    ["仅展示合法操作", ["仅展示合法操作", "Valid actions only"]],
    ["RULE MATRIX", ["规则矩阵", "RULE MATRIX"]],
    ["状态机规则矩阵", ["状态机规则矩阵", "State-machine rule matrix"]],
    ["与后端 `PaymentStateMachine` 对齐", ["与后端付款状态机保持一致", "Aligned with backend PaymentStateMachine"]],
    ["审计记录预览", ["审计记录预览", "Audit preview"]],
    ["选中付款后，展示最新状态变更、触发方与失败原因。", ["选中付款后，展示最新状态变更、触发方与失败原因。", "Select a payment to view recent transitions, triggers, and failure reasons."]],
    ["付款生命周期", ["付款生命周期", "Payment lifecycle"]],
    ["LIFECYCLE", ["生命周期", "LIFECYCLE"]],
    ["每一步，都清晰可追溯", ["每一步，都清晰可追溯", "Every step is traceable"]],
    ["后端状态机定义了合法流转路径，前端仅展示并驱动这些规则允许的动作。", ["后端状态机定义了合法流转路径，前端仅展示并驱动这些规则允许的动作。", "The backend defines valid transitions; the interface only exposes permitted actions."]],
    ["创建", ["创建", "Created"]],
    ["校验", ["校验", "Validated"]],
    ["发送", ["发送", "Sent"]],
    ["完成", ["完成", "Completed"]],
    ["失败分支：CREATED / VALIDATED / SENT", ["失败分支：已创建 / 已校验 / 已发送", "Failure branches: CREATED / VALIDATED / SENT"]],
    ["PAYMENT DETAIL", ["付款详情", "PAYMENT DETAIL"]],
    ["付款详情", ["付款详情", "Payment details"]],
    ["关闭详情", ["关闭详情", "Close details"]],
    ["NEW PAYMENT", ["新建付款", "NEW PAYMENT"]],
    ["创建付款指令", ["创建付款指令", "Create payment instruction"]],
    ["请核对付款信息，提交后将生成唯一付款编号。", ["请核对付款信息，提交后将生成唯一付款编号。", "Review the payment details. A unique payment ID will be generated on submission."]],
    ["关闭", ["关闭", "Close"]],
    ["付款账户", ["付款账户", "Source account"]],
    ["收款账户", ["收款账户", "Destination account"]],
    ["付款金额", ["付款金额", "Payment amount"]],
    ["币种", ["币种", "Currency"]],
    ["付款用途", ["付款用途", "Payment reference"]],
    ["例如：ACC-CN-001", ["例如：ACC-CN-001", "Example: ACC-CN-001"]],
    ["例如：ACC-CN-002", ["例如：ACC-CN-002", "Example: ACC-CN-002"]],
    ["例如：7 月供应商服务费", ["例如：7 月供应商服务费", "Example: July supplier service fee"]],
    ["取消", ["取消", "Cancel"]],
    ["提交付款", ["提交付款", "Submit payment"]],
    ["当前状态", ["当前状态", "Current status"]],
    ["状态变化历史", ["状态变化历史", "Status history"]],
    ["暂无历史记录", ["暂无历史记录", "No history records"]],
    ["处理此付款", ["处理此付款", "Process payment"]],
    ["处理中…", ["处理中…", "Processing…"]],
    ["刷新详情", ["刷新详情", "Refresh details"]],
    ["付款处理完成", ["付款处理完成", "Payment processing completed"]],
    ["付款处理失败", ["付款处理失败", "Payment processing failed"]],
    ["付款处理失败，请联系系统管理员。", ["付款处理失败，请联系系统管理员。", "Payment processing failed. Contact the system administrator."]],
    ["正在加载付款详情…", ["正在加载付款详情…", "Loading payment details…"]],
    ["请输入付款账户", ["请输入付款账户", "Enter the source account"]],
    ["请输入收款账户", ["请输入收款账户", "Enter the destination account"]],
    ["付款账户与收款账户不能相同", ["付款账户与收款账户不能相同", "Source and destination accounts must be different"]],
    ["金额须在 0.01 至 1,000,000.00 之间", ["金额须在 0.01 至 1,000,000.00 之间", "Amount must be between 0.01 and 1,000,000.00"]],
    ["付款方账户不在系统账户库中", ["付款方账户不在系统账户库中", "The source account is not registered"]],
    ["网络延迟超过 10 秒，重试三次后仍未成功", ["网络延迟超过 10 秒，重试三次后仍未成功", "Network delay exceeded 10 seconds and all three retries failed"]],
    ["付款网络暂时不可用", ["付款网络暂时不可用", "The payment network is temporarily unavailable"]],
    ["付款金额不符合业务规则", ["付款金额不符合业务规则", "The payment amount violates business rules"]],
    ["付款币种不符合业务规则", ["付款币种不符合业务规则", "The payment currency violates business rules"]],
    ["付款账户字段不符合业务规则", ["付款账户字段不符合业务规则", "The account fields violate business rules"]],
    ["Source account does not exist", ["付款方账户不存在", "Source account does not exist"]],
    ["Payment processing completed", ["付款处理完成", "Payment processing completed"]],
    ["Payment processed successfully", ["付款处理成功", "Payment processed successfully"]],
    ["Payment processing failed", ["付款处理失败", "Payment processing failed"]],
    ["Payment validation passed", ["付款校验通过", "Payment validation passed"]],
    ["Payment sent to target system", ["付款已发送至目标系统", "Payment sent to target system"]],
    ["Payment processing completed", ["付款处理完成", "Payment processing completed"]],
    ["Payer account validation failed", ["付款方账户校验失败", "Payer account validation failed"]]
    ,["终态", ["终态", "Terminal"]]
    ,["先选择一笔付款", ["先选择一笔付款", "Select a payment first"]]
    ,["从列表中点击任意付款，右侧会同步展示状态、合法下一状态、处理动作和审计轨迹。", ["从列表中点击任意付款，右侧会同步展示状态、合法下一状态、处理动作和审计轨迹。", "Select any payment in the list to view its status, valid next states, actions, and audit trail."]]
    ,["与后端状态机联动的前端控制视图，帮助运营理解当前阶段与允许动作。", ["与后端状态机联动的前端控制视图，帮助运营理解当前阶段与允许动作。", "A control view linked to the backend state machine, showing the current stage and allowed actions."]]
    ,["允许下一状态", ["允许下一状态", "Allowed next states"]]
    ,["最近变更", ["最近变更", "Last updated"]]
    ,["错误信息", ["错误信息", "Error information"]]
    ,["无", ["无", "None"]]
    ,["付款已到达终态，前端将禁用进一步处理动作，只保留查看与刷新。", ["付款已到达终态，前端将禁用进一步处理动作，只保留查看与刷新。", "This payment is terminal. Further processing is disabled; view and refresh remain available."]]
    ,["失败分支", ["失败分支", "Failure branch"]]
    ,["规则说明：", ["规则说明：", "Rule: "]]
    ,["在 CREATED、VALIDATED、SENT 任一阶段都允许转入 FAILED，前端以审计轨迹说明失败发生点。", ["在已创建、已校验、已发送任一阶段都允许转入失败，前端以审计轨迹说明失败发生点。", "A payment may fail from CREATED, VALIDATED, or SENT; the audit trail identifies the failure stage."]]
    ,["选择一笔付款后可查看它是否命中过失败路径，以及失败发生在哪个阶段。", ["选择一笔付款后可查看它是否命中过失败路径，以及失败发生在哪个阶段。", "Select a payment to see whether it entered a failure path and at which stage."]]
    ,["暂无状态上下文", ["暂无状态上下文", "No state context"]]
    ,["选中付款后，这里会展示当前状态允许流转到的下一状态集合。", ["选中付款后，这里会展示当前状态允许流转到的下一状态集合。", "Select a payment to see the next states allowed from its current state."]]
    ,["当前为终态", ["当前为终态", "Terminal state"]]
    ,["当前可继续流转", ["当前可继续流转", "Transition available"]]
    ,["可流转到", ["可流转到", "can transition to"]]
    ,["这里展示的是后端 PaymentStateMachine#getAllowedNextStatuses 在前端的镜像规则。", ["这里展示的是后端付款状态机允许下一状态规则的前端镜像。", "This mirrors the backend PaymentStateMachine allowed-next-state rules."]]
    ,["没有下一状态", ["没有下一状态", "No next state"]]
    ,["当前付款已经处于终态，后端规则不允许再继续推进状态。", ["当前付款已经处于终态，后端规则不允许再继续推进状态。", "The payment is terminal and cannot transition further."]]
    ,["暂无可执行动作", ["暂无可执行动作", "No available actions"]]
    ,["选择付款后，这里会根据当前状态展示合法操作，例如处理付款或刷新状态。", ["选择付款后，这里会根据当前状态展示合法操作，例如处理付款或刷新状态。", "Select a payment to view valid actions such as processing or refreshing."]]
    ,["刷新当前付款", ["刷新当前付款", "Refresh current payment"]]
    ,["重新拉取付款详情与审计记录，确保前端展示与数据库一致。", ["重新拉取付款详情与审计记录，确保前端展示与数据库一致。", "Reload payment details and audit records to match the database."]]
    ,["推进状态机处理", ["推进状态机处理", "Process through state machine"]]
    ,["调用后端 POST /api/payments/{id}/process，由后端依次执行校验、发送、确认与失败分支控制。", ["调用后端付款处理接口，由后端依次执行校验、发送、确认与失败分支控制。", "Call the backend process endpoint to run validation, sending, confirmation, and failure handling."]]
    ,["当前状态不可再推进", ["当前状态不可再推进", "Current state cannot advance"]]
    ,["等待后端继续推进", ["等待后端继续推进", "Awaiting backend processing"]]
    ,["该付款已到达终态，前端仅保留查看与审计能力。", ["该付款已到达终态，前端仅保留查看与审计能力。", "This payment is terminal; only viewing and auditing remain available."]]
    ,["当前付款不在 CREATED 状态，前端不会展示人工推进按钮，以免绕过后端规则。", ["当前付款不在已创建状态，前端不会展示人工推进按钮，以免绕过后端规则。", "The payment is not in CREATED state, so manual processing is hidden to protect backend rules."]]
    ,["终态只读", ["终态只读", "Terminal: read only"]]
    ,["受后端规则控制", ["受后端规则控制", "Controlled by backend rules"]]
    ,["等待付款选择", ["等待付款选择", "Awaiting payment selection"]]
    ,["审计预览会在你选中一笔付款后展示最近状态变更、触发方和错误信息。", ["审计预览会在你选中一笔付款后展示最近状态变更、触发方和错误信息。", "Select a payment to view recent transitions, triggers, and errors."]]
    ,["付款创建", ["付款创建", "Payment created"]]
    ,["暂无审计历史", ["暂无审计历史", "No audit history"]]
    ,["当前付款还没有历史记录返回。", ["当前付款还没有历史记录返回。", "No history records were returned for this payment."]]
    ,["查看付款详情", ["查看付款详情", "View payment details"]]
    ,["付款数据已更新", ["付款数据已更新", "Payment data updated"]]
]);

let currentLanguage = "zh";
const originalText = new WeakMap();
const originalAttributes = new WeakMap();

function translateValue(value, language = currentLanguage) {
    const normalized = value.trim();
    const direct = messages.get(normalized);
    if (direct) return direct[language === "en" ? 1 : 0];

    const patterns = [
        [/^共 (\d+) 笔付款$/, (m) => language === "en" ? `${m[1]} payments` : `共 ${m[1]} 笔付款`],
        [/^付款 (.+) 已创建$/, (m) => language === "en" ? `Payment ${m[1]} created` : `付款 ${m[1]} 已创建`],
        [/^（共尝试 (\d+) 次）$/, (m) => language === "en" ? ` (${m[1]} attempts)` : `（共尝试 ${m[1]} 次）`],
        [/^当前付款：(.+)$/, (m) => language === "en" ? `Current payment: ${m[1]}` : `当前付款：${m[1]}`]
    ];
    for (const [pattern, formatter] of patterns) {
        const match = normalized.match(pattern);
        if (match) return formatter(match);
    }
    if (language === "en") {
        let translated = normalized;
        const phrases = [...messages.entries()]
            .filter(([source]) => /[\u3400-\u9fff]/.test(source) && normalized.includes(source))
            .sort((a, b) => b[0].length - a[0].length);
        phrases.forEach(([source, values]) => {
            translated = translated.replaceAll(source, values[1]);
        });
        if (translated !== normalized) return translated;
    }
    return normalized;
}

function translateTextNode(node) {
    if (!node.nodeValue || !node.nodeValue.trim()) return;
    if (!originalText.has(node)) originalText.set(node, node.nodeValue.trim());
    const source = originalText.get(node);
    const leading = node.nodeValue.match(/^\s*/)?.[0] || "";
    const trailing = node.nodeValue.match(/\s*$/)?.[0] || "";
    node.nodeValue = `${leading}${translateValue(source)}${trailing}`;
}

function translateElementAttributes(element) {
    const names = ["placeholder", "aria-label", "title", "content"];
    if (!originalAttributes.has(element)) originalAttributes.set(element, new Map());
    const originals = originalAttributes.get(element);
    names.forEach((name) => {
        if (!element.hasAttribute(name)) return;
        if (!originals.has(name)) originals.set(name, element.getAttribute(name));
        element.setAttribute(name, translateValue(originals.get(name)));
    });
}

export function translatePage(root = document.body) {
    if (!root) return;
    if (root.nodeType === Node.TEXT_NODE) {
        translateTextNode(root);
        return;
    }
    if (root.nodeType !== Node.ELEMENT_NODE) return;
    translateElementAttributes(root);
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
    let node = walker.nextNode();
    while (node) {
        if (node.nodeType === Node.TEXT_NODE) translateTextNode(node);
        else translateElementAttributes(node);
        node = walker.nextNode();
    }
    document.documentElement.lang = currentLanguage === "en" ? "en" : "zh-CN";
    document.title = currentLanguage === "en"
        ? "Midnight Pay · Payment Operations Dashboard"
        : "Midnight Pay · 付款处理工作台";
    document.querySelectorAll("[data-language]").forEach((button) => {
        const active = button.dataset.language === currentLanguage;
        button.classList.toggle("active", active);
        button.setAttribute("aria-pressed", String(active));
    });
}

export function setLanguage(language) {
    currentLanguage = language === "en" ? "en" : "zh";
    translatePage();
    window.dispatchEvent(new CustomEvent("languagechange", { detail: { language: currentLanguage } }));
}

export function getLanguage() {
    return currentLanguage;
}

export function localize(zh, en) {
    return currentLanguage === "en" ? en : zh;
}

export function initializeI18n() {
    document.querySelectorAll("[data-language]").forEach((button) => {
        button.addEventListener("click", () => setLanguage(button.dataset.language));
    });
    translatePage();
    const observer = new MutationObserver((mutations) => {
        mutations.forEach((mutation) => mutation.addedNodes.forEach((node) => translatePage(node)));
    });
    observer.observe(document.body, { childList: true, subtree: true });
}
