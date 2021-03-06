package com.nitobi.jsf.listener;

import com.nitobi.exception.NitobiException;
import com.nitobi.jsf.event.NitobiSaveEvent;
import com.nitobi.server.handler.SaveHandler;

import javax.el.ELException;
import javax.el.MethodExpression;
import javax.faces.FacesException;
import javax.faces.context.FacesContext;
import javax.faces.event.PhaseEvent;
import javax.faces.event.PhaseId;
import javax.faces.event.PhaseListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author eric
 */
public class NitobiSaveHandlerPhaseListener implements PhaseListener {

    public static final String NITOBI_AJAX_TRIGGER = "nitobi_save_trigger";
    private static final String MEX_PREFIX = "#{";
    private static final String MEX_SUFFIX = "}";
    private static Logger logger = Logger.getLogger(NitobiGetPhaseListener.class.getName());

    /**
     * @param phaseEvent
     */
    public void afterPhase(PhaseEvent phaseEvent) {
        FacesContext context = phaseEvent.getFacesContext();
        String viewId = context.getViewRoot().getViewId();

        int triggerPosition = viewId.indexOf(NITOBI_AJAX_TRIGGER);
        if (triggerPosition > -1) {
            /*
             * Grab the method expression from the viewId
             */
            String theExpression = MEX_PREFIX + viewId.substring(triggerPosition + NITOBI_AJAX_TRIGGER.length() + 1) + MEX_SUFFIX;
            theExpression = theExpression.replace('/', '.');
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Nitobi Get Phase Listener attempting method expression: " + theExpression);
            }

            /*
             * Set up the method expression to take a GetHandler object
             */
            Class[] argTypes = {NitobiSaveEvent.class};
            MethodExpression methodExpression = null;
            try {
                methodExpression = context.getApplication().getExpressionFactory().createMethodExpression(context.getELContext(), theExpression, null, argTypes);
            } catch (ELException e) {
                logger.warning("NitobiSaveHandlerPhaseListener could not generate the method expression for " + theExpression + " -- " + e.getMessage());
            } catch (NullPointerException e) {
                logger.warning("NitobiSaveHandlerPhaseListener received a NullPointerException while trying to create the methodExpression for " + theExpression + " -- " + e.getMessage());
            }

            /*
             * Invoke the method in managed bean, passing it a GetHandler
             */
            HttpServletRequest request = (HttpServletRequest) context.getExternalContext().getRequest();
            HttpServletResponse response = (HttpServletResponse) context.getExternalContext().getResponse();
            SaveHandler savehandler = null;
            try {
                savehandler = new SaveHandler(request);
            } catch (NitobiException e) {
                logger.warning("Could not generate SaveHandler from the request object: " + e.getMessage());
                throw new FacesException("Could not generate SaveHandler object.");
            }

            NitobiSaveEvent saveEvent = new NitobiSaveEvent(savehandler);

            Object[] args = {saveEvent};
            try {
                methodExpression.invoke(context.getELContext(), args);
            } catch (NullPointerException e) {
                logger.warning("Nitobi SaveHandler Phase Listener received a NullPointerException while trying to invoke the methodExpression for " + theExpression + " -- " + e.getMessage());
            } catch (ELException e) {
                logger.warning("Nitobi SaveHandler Phase Listener could not invoke the method for " + theExpression + " -- " + e.getMessage());
            }

            /*
             * Grab the response object and send reply to client.
             */
            try {
                savehandler.writeToClient(response);
            } catch (NitobiException e) {
                logger.severe("The NitobiSaveHandlerPhaseListener could not send a response to the client due to a NitobiException: " + e.getMessage());
            }

            /*
            * End the JSF lifecycle
            */
            context.responseComplete();
        }
    }

    public void beforePhase(PhaseEvent arg0) {
        // Don't need to do anything here
    }

    public PhaseId getPhaseId() {
        return PhaseId.RESTORE_VIEW;
    }
}