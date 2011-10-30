package password.pwm.util;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.jasig.cas.client.util.AbstractCasFilter;
import org.jasig.cas.client.util.CommonUtils;
import org.jasig.cas.client.util.XmlUtils;
import org.jasig.cas.client.validation.Assertion;
import password.pwm.AuthenticationFilter;
import password.pwm.ContextManager;
import password.pwm.PwmApplication;
import password.pwm.PwmSession;
import password.pwm.bean.SessionStateBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class CASAuthenticationHelper {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(AuthenticationFilter.class.getName());

    public static boolean authUserUsingCASClearPass(
            final HttpServletRequest req,
            final String clearPassUrl
    )
            throws UnsupportedEncodingException, PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final HttpSession session = req.getSession();

        //make sure user session isn't already authenticated
        if (ssBean.isAuthenticated()) {
            return false;
        }

        // get CAS assertion out of the header (if it exists);
        final Assertion assertion = (Assertion) session.getAttribute(AbstractCasFilter.CONST_CAS_ASSERTION);
        if (assertion == null) {
            LOGGER.trace(pwmSession,"no CAS assertion header present, skipping CAS authentication attempt");
            return false;
        }

        // get cas proxy ticket
        final String proxyTicket = assertion.getPrincipal().getProxyTicketFor(clearPassUrl);
        if (proxyTicket == null) {
            LOGGER.trace(pwmSession,"no CAS proxy ticket available, skipping CAS authentication attempt");
            return false;
        }

        final String clearPassRequestUrl = clearPassUrl + "?" + "ticket="
                + proxyTicket + "&" + "service="
                + URLEncoder.encode(clearPassUrl, "UTF-8");

        final String response = CommonUtils.getResponseFromServer(
                clearPassRequestUrl, "UTF-8");

        final String username = assertion.getPrincipal().getName();
        final String password = XmlUtils.getTextForElement(response, "credentials");

        if (password == null || password.length() < 1) {
            final String errorMsg = "CAS server did not return credentials for user '" + username + "'";
            LOGGER.trace(pwmSession, errorMsg);
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_WRONGPASSWORD,errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        //user isn't already authenticated and has CAS assertion and password, so try to auth them.
        LOGGER.debug(pwmSession, "attempting to authenticate user '" + username + "' using CAS assertion and password");
        AuthenticationFilter.authenticateUser(username, password, null,  pwmSession, pwmApplication, req.isSecure());
        return true;
    }
}