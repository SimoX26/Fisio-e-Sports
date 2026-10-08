import http from "node:http";
import { URL } from "node:url";
import makeWASocket, {
  DisconnectReason,
  fetchLatestBaileysVersion,
  useMultiFileAuthState
} from "@whiskeysockets/baileys";
import Pino from "pino";
import QRCode from "qrcode";
import qrcodeTerminal from "qrcode-terminal";

const port = Number.parseInt(process.env.BAILEYS_PORT || "3001", 10);
const sessionDir = process.env.BAILEYS_SESSION_DIR || "auth-session";

const runtimeState = {
  ready: false,
  qrRequired: false,
  state: "BOOTING",
  lastError: null,
  qr: null,
  qrDataUrl: null,
  qrUpdatedAt: null,
  qrCounter: 0,
  socket: null
};

function writeJson(response, statusCode, payload) {
  response.writeHead(statusCode, { "Content-Type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(payload));
}

function writeHtml(response, statusCode, html) {
  response.writeHead(statusCode, {
    "Content-Type": "text/html; charset=utf-8",
    "Cache-Control": "no-store, no-cache, must-revalidate, proxy-revalidate",
    "Pragma": "no-cache",
    "Expires": "0"
  });
  response.end(html);
}

function collectRequestBody(request) {
  return new Promise((resolve, reject) => {
    let body = "";
    request.on("data", (chunk) => {
      body += chunk;
    });
    request.on("end", () => resolve(body));
    request.on("error", reject);
  });
}

function normalizePhone(value) {
  if (!value || typeof value !== "string") {
    return null;
  }
  let digitsOnly = value.trim().replace(/[^0-9]/g, "");
  if (digitsOnly.startsWith("00")) {
    digitsOnly = digitsOnly.slice(2);
  }
  if (digitsOnly.length < 8 || digitsOnly.length > 15) {
    return null;
  }
  return digitsOnly;
}

function normalizeMessage(value) {
  if (!value || typeof value !== "string") {
    return null;
  }
  const normalized = value.trim();
  return normalized.length === 0 ? null : normalized;
}

async function startBaileys() {
  try {
    runtimeState.state = "STARTING";
    runtimeState.lastError = null;

    const { state, saveCreds } = await useMultiFileAuthState(sessionDir);
    const { version } = await fetchLatestBaileysVersion();
    const socket = makeWASocket({
      version,
      auth: state,
      printQRInTerminal: false,
      logger: Pino({ level: process.env.BAILEYS_LOG_LEVEL || "silent" }),
      browser: ["Fisio e Sports", "Desktop", "1.0.0"]
    });

    runtimeState.socket = socket;

    socket.ev.on("creds.update", saveCreds);
    socket.ev.on("connection.update", async (update) => {
      const { connection, lastDisconnect, qr } = update;

      if (qr) {
        runtimeState.qr = qr;
        runtimeState.qrRequired = true;
        runtimeState.ready = false;
        runtimeState.state = "QR_REQUIRED";
        runtimeState.qrUpdatedAt = new Date().toISOString();
        runtimeState.qrCounter += 1;
        runtimeState.qrDataUrl = await QRCode.toDataURL(qr, {
          errorCorrectionLevel: "M",
          margin: 4,
          scale: 10
        });
        if (process.env.BAILEYS_PRINT_QR_TERMINAL !== "0") {
          qrcodeTerminal.generate(qr, { small: true });
        }
        console.log("[baileys-service] QR #" + runtimeState.qrCounter
            + " disponibile anche su http://localhost:" + port + "/api/qr");
      }

      if (connection === "open") {
        runtimeState.ready = true;
        runtimeState.qrRequired = false;
        runtimeState.state = "CONNECTED";
        runtimeState.qr = null;
        runtimeState.qrDataUrl = null;
        runtimeState.qrUpdatedAt = null;
        runtimeState.lastError = null;
        console.log("[baileys-service] Connessione WhatsApp attiva.");
      }

      if (connection === "close") {
        runtimeState.ready = false;
        runtimeState.qrRequired = false;
        runtimeState.state = "DISCONNECTED";
        const statusCode = lastDisconnect?.error?.output?.statusCode;
        const shouldReconnect = statusCode !== DisconnectReason.loggedOut;
        runtimeState.lastError = lastDisconnect?.error?.message || "Connessione WhatsApp chiusa.";

        if (shouldReconnect) {
          console.log("[baileys-service] Connessione chiusa, riconnessione in corso...");
          startBaileys();
        } else {
          runtimeState.state = "LOGGED_OUT";
          runtimeState.lastError = "Sessione scollegata. Esegui il reset sessione e scansiona un nuovo QR.";
        }
      }
    });
  } catch (error) {
    runtimeState.ready = false;
    runtimeState.qrRequired = false;
    runtimeState.state = "ERROR";
    runtimeState.lastError = error && error.message ? error.message : "Errore sconosciuto Baileys";
    console.error("[baileys-service] Bootstrap failed:", error);
  }
}

const server = http.createServer(async (request, response) => {
  const requestUrl = new URL(request.url, `http://127.0.0.1:${port}`);

  if (request.method === "GET" && requestUrl.pathname === "/api/status") {
    writeJson(response, 200, {
      ready: runtimeState.ready,
      qrRequired: runtimeState.qrRequired,
      state: runtimeState.state,
      lastError: runtimeState.lastError,
      qrUpdatedAt: runtimeState.qrUpdatedAt,
      qrCounter: runtimeState.qrCounter
    });
    return;
  }

  if (request.method === "GET" && requestUrl.pathname === "/api/qr") {
    if (!runtimeState.qrDataUrl) {
      writeHtml(response, 200, "<!doctype html><html><head><meta charset=\"utf-8\">"
          + "<title>Baileys QR</title></head>"
          + "<body><p>QR non disponibile. Stato: "
          + runtimeState.state + "</p></body></html>");
      return;
    }
    writeHtml(response, 200, "<!doctype html><html><head><meta charset=\"utf-8\">"
        + "<title>Baileys QR</title>"
        + "<style>html,body{width:100%;height:100%;margin:0;padding:0;background:#f7f4ee;color:#202124;font-family:sans-serif;overflow:hidden}"
        + "body{display:grid;place-items:center}main{text-align:center;padding:12px;box-sizing:border-box;max-width:100%}"
        + ".qr{background:#fff;padding:14px;border-radius:18px;box-shadow:0 12px 30px #0002;display:inline-block;max-width:100%;box-sizing:border-box}"
        + "img{width:min(100%,360px);height:auto;display:block}h1{font-size:1.05rem;margin:0 0 12px}</style></head><body><main>"
        + "<h1>Scansiona il QR WhatsApp</h1><div class=\"qr\"><img src=\"" + runtimeState.qrDataUrl + "\" alt=\"QR WhatsApp\"></div>"
        + "</main></body></html>");
    return;
  }

  if (request.method === "POST" && requestUrl.pathname === "/api/shutdown") {
    writeJson(response, 200, { success: true });
    setTimeout(shutdown, 50);
    return;
  }

  if (request.method === "POST" && requestUrl.pathname === "/api/send") {
    if (!runtimeState.socket || !runtimeState.ready) {
      writeJson(response, 503, {
        error: runtimeState.qrRequired
          ? "Baileys non autenticato. Scansiona il QR."
          : "Baileys non pronto."
      });
      return;
    }

    try {
      const body = await collectRequestBody(request);
      const payload = JSON.parse(body || "{}");
      const recipient = normalizePhone(payload.recipient);
      const message = normalizeMessage(payload.message);

      if (!recipient) {
        writeJson(response, 400, { error: "Numero destinatario non valido." });
        return;
      }
      if (!message) {
        writeJson(response, 400, { error: "Messaggio obbligatorio." });
        return;
      }

      const matches = await runtimeState.socket.onWhatsApp(`${recipient}@s.whatsapp.net`);
      const match = Array.isArray(matches) ? matches.find((item) => item?.exists) : null;
      if (!match?.jid) {
        writeJson(response, 404, { error: "Il numero non risulta attivo su WhatsApp." });
        return;
      }

      const result = await runtimeState.socket.sendMessage(match.jid, { text: message });
      const messageId = result?.key?.id || "";
      if (!messageId) {
        writeJson(response, 502, { error: "WhatsApp non ha confermato l'invio del messaggio." });
        return;
      }
      writeJson(response, 200, { success: true, messageId });
    } catch (error) {
      writeJson(response, 500, {
        error: error && error.message ? error.message : "Errore interno Baileys"
      });
    }
    return;
  }

  writeJson(response, 404, { error: "Not found" });
});

server.listen(port, "127.0.0.1", () => {
  console.log(`[baileys-service] listening on http://127.0.0.1:${port}`);
  startBaileys();
});

function shutdown() {
  try {
    runtimeState.socket?.end?.();
  } catch (error) {
    console.error("[baileys-service] Error during shutdown:", error);
  }
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 4000).unref();
}

process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
