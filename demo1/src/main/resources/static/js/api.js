const API_BASE = "/api/payments";

async function request(path = "", options = {}) {
    const response = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...options.headers
        }
    });

    const contentType = response.headers.get("content-type") || "";
    const payload = contentType.includes("application/json") ? await response.json() : null;

    if (!response.ok) {
        const error = new Error(payload?.message || payload?.errorMessage || `请求失败（${response.status}）`);
        error.code = payload?.errorCode;
        error.status = response.status;
        throw error;
    }

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
