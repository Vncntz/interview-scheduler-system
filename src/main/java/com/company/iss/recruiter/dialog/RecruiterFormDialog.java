package com.company.iss.recruiter.dialog;

import com.company.iss.auth.entity.User;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.service.BranchService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import org.springframework.beans.factory.annotation.Autowired;

public class RecruiterFormDialog extends Dialog {

    private final Binder<User> binder = new Binder<>(User.class);
    private final User user;
    private final SaveListener saveListener;

    public interface SaveListener {
        void onSave(User user, String temporaryPassword);
    }

    public RecruiterFormDialog(User user,BranchService branchService, SaveListener saveListener) {
        this.user = user;
        this.saveListener = saveListener;

        setHeaderTitle(user.getId() == null ? "Add New Recruiter" : "Edit Recruiter: " + user.getFullName());

        TextField fullName = new TextField("Full Name");
        EmailField email = new EmailField("Email Address");
        PasswordField temporaryPassword = new PasswordField("Temporary Password");

        ComboBox<Branch> branch = new ComboBox<>("Assigned Branch");
        branch.setItems(branchService.findAll());
        branch.setItemLabelGenerator(Branch::getBranchName);

        if (user.getId() != null) {
            temporaryPassword.setVisible(false);
        }

        binder.forField(fullName).asRequired("Full name is required").bind(User::getFullName, User::setFullName);

        binder.forField(email).asRequired("Email is required").bind(User::getEmail, User::setEmail);

        binder.forField(branch).asRequired("Branch is required").bind(User::getBranch, User::setBranch);

        binder.readBean(user);

        FormLayout formLayout = new FormLayout(fullName, email, branch, temporaryPassword);

        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("300px", 2));

        add(formLayout);

        Button saveButton = new Button("Save", e -> saveEntity(temporaryPassword.getValue()));
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", e -> close());

        getFooter().add(cancelButton, saveButton);
    }

    private void saveEntity(String temporaryPassword) {
        try {
            binder.writeBean(user);
            saveListener.onSave(user, temporaryPassword);
            close();

        } catch (Exception e) {
            Notification.show("Please fix the validation errors before saving.", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}