package it.SimoSW.controller.graphic;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/address-book/create")
public class AddressBookCreatePageServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        if (PostSubmitNavigationGuard.redirectIfBlocked(request, response, "/address-book/create")) {
            return;
        }

        request.getRequestDispatcher("/WEB-INF/jsp/therapist/addressBookCreate.jsp").forward(request, response);
    }
}
