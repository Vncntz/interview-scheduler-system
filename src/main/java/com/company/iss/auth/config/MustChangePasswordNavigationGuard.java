package com.company.iss.auth.config;

import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.auth.view.ChangePasswordView;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

@Component
public class MustChangePasswordNavigationGuard implements VaadinServiceInitListener {

    private final SecurityService securityService;

    public MustChangePasswordNavigationGuard(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiEvent ->
                uiEvent.getUI().addBeforeEnterListener(beforeEnter -> {
                    User user = securityService.getCurrentUser();
                    String path = beforeEnter.getLocation().getPath();
                    if (user != null && user.isActive() && user.isMustChangePassword()
                            && !"change-password".equals(path)) {
                        beforeEnter.forwardTo(ChangePasswordView.class);
                    }
                })
        );
    }
}
