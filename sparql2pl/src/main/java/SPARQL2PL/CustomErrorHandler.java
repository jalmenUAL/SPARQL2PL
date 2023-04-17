package SPARQL2PL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.server.ErrorEvent;
import com.vaadin.flow.server.ErrorHandler;

public class CustomErrorHandler implements ErrorHandler {

    private static final Logger logger = LoggerFactory.getLogger(CustomErrorHandler.class);

    @Override
    public void error(ErrorEvent errorEvent) {
        logger.error("Something wrong happened", errorEvent.getThrowable());
        if(UI.getCurrent() != null) {
            UI.getCurrent().access(() -> {
                show_notification("An internal error has occurred.",
                		errorEvent.getThrowable().getMessage());
            });
        }
    }

    public void show_notification(String type, String message) {
		Notification notification = Notification.show(type + " " + message);
		notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
		notification.setPosition(Notification.Position.MIDDLE);
	} 
}
