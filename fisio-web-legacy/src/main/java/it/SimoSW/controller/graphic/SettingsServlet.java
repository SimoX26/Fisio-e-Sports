package it.SimoSW.controller.graphic;

import it.SimoSW.service.whatsapp.WhatsAppBaileysService;
import it.SimoSW.util.bootstrap.ApplicationInitializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/settings/*")
public class SettingsServlet extends HttpServlet {

    private WhatsAppBaileysService whatsAppBaileysService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void init() {
        ApplicationInitializer initializer = (ApplicationInitializer) getServletContext().getAttribute("appInitializer");
        this.whatsAppBaileysService = initializer.getWhatsAppBaileysService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if ("/baileys-status".equals(request.getPathInfo())) {
            response.setContentType("application/json; charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            mapper.writeValue(response.getWriter(), whatsAppBaileysService.getStatus());
            return;
        }

        if ("/whatsapp-qr".equals(request.getPathInfo())) {
            response.setContentType("text/html; charset=UTF-8");
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.getWriter().write(whatsAppBaileysService.readQrHtml());
            return;
        }

        request.setAttribute("baileysStatus", whatsAppBaileysService.getStatus());
        request.getRequestDispatcher("/WEB-INF/jsp/settings.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String action = request.getParameter("action");
        if ("start-baileys".equals(action)) {
            try {
                whatsAppBaileysService.startService();
                request.setAttribute("success", "Avvio del servizio WhatsApp richiesto. Attendi qualche secondo e aggiorna lo stato.");
            } catch (RuntimeException ex) {
                request.setAttribute("error", ex.getMessage());
            }
        } else if ("stop-baileys".equals(action)) {
            try {
                whatsAppBaileysService.stopService();
                request.setAttribute("success", "Arresto del servizio WhatsApp richiesto.");
            } catch (RuntimeException ex) {
                request.setAttribute("error", ex.getMessage());
            }
        }

        doGet(request, response);
    }
}
