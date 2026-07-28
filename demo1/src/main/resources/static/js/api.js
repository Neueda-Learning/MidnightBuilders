const API_BASE = "/api/payments";

const API_LOG_PREFIX = "[PaymentAPI]";

function maskToken(value) {
    if (!value) {
        return "<empty>";
    }
    if (value.length <= 10) {
        return `${value.slice(0, 2)}***${value.slice(-2)}`;
    }
    return `${value.slice(0, 6)}***${value.slice(-4)}`;
}

function sanitizePayload(payload) {
    if (!payload || typeof payload !== "object") {
        return payload;
    }
    return {
        ...payload,
        sourceAccount: payload.sourceAccount ? `***${String(payload.sourceAccount).slice(-4)}` : undefined,
        destinationAccount: payload.destinationAccount ? `***${String(payload.destinationAccount).slice(-4)}` : undefined,
        reference: payload.reference ? `<len:${String(payload.reference).length}>` : payload.reference
    };
}

async function request(path = "", options = {}) {
    const method = options.method || "GET";
    const url = `${API_BASE}${path}`;
    const headers = {
        "Content-Type": "application/json",
        ...options.headers
    };
    const startedAt = performance.now();

    console.info(`${API_LOG_PREFIX} Request started`, {
        method,
        url,
        idempotencyKey: maskToken(headers["Idempotency-Key"] || headers["idempotency-key"]),
        payload: options.body ? sanitizePayload(JSON.parse(options.body)) : null
    });

    let response;
    try {
        response = await fetch(url, {
            ...options,
            headers
        });
    } catch (error) {
        console.error(`${API_LOG_PREFIX} Network request failed`, {
            method,
            url,
            durationMs: Math.round(performance.now() - startedAt),
            message: error?.message
        });
        throw error;
    }

    const contentType = response.headers.get("content-type") || "";
    const payload = contentType.includes("application/json") ? await response.json() : null;
    const durationMs = Math.round(performance.now() - startedAt);

    if (!response.ok) {
        console.warn(`${API_LOG_PREFIX} Request failed`, {
            method,
            url,
            status: response.status,
            durationMs,
            errorCode: payload?.errorCode,
            message: payload?.message || payload?.errorMessage
        });
        const error = new Error(payload?.message || payload?.errorMessage || `请求失败（${response.status}）`);
        error.code = payload?.errorCode;
        error.status = response.status;
        throw error;
    }

    console.info(`${API_LOG_PREFIX} Request completed`, {
        method,
        url,
        status: response.status,
        durationMs
    });

    return payload;
}

export const paymentApi = {
    list(status = "") {
        const query = status ? `?status=${encodeURIComponent(status)}` : "";
        return request(query);
    },
    get(id) {
        return request(`/${encodeURIComponent(id)}`);
    },
    history(id) {
        return request(`/${encodeURIComponent(id)}/history`);
    },
    create(payment, idempotencyKey) {
        return request("", {
            method: "POST",
            headers: { "Idempotency-Key": idempotencyKey },
            body: JSON.stringify(payment)
        });
    },
    process(id) {
        return request(`/${encodeURIComponent(id)}/process`, { method: "POST" });
    }
};
