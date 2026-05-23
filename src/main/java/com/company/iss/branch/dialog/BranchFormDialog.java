package com.company.iss.branch.dialog;

import com.company.iss.branch.entity.Branch;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

public class BranchFormDialog extends Dialog {

    private final Binder<Branch> binder = new Binder<>(Branch.class);
    private final Branch branch;
    private final SaveListener saveListener;

    public interface SaveListener {
        void onSave(Branch branch);
    }

    public BranchFormDialog(Branch branch, SaveListener saveListener) {
        this.branch = branch;
        this.saveListener = saveListener;

        setHeaderTitle(branch.getId() == null ? "Add New Branch" : "Edit Branch: " + branch.getBranchName());

        // Create Form Input Fields
        TextField branchCode = new TextField("Branch Code");
        TextField branchName = new TextField("Branch Name");
        TextField address = new TextField("Address");
        TextField city = new TextField("City");
        TextField province = new TextField("Province");

        // Bind fields matching property names in your Lombok entity
        binder.forField(branchCode).asRequired("Branch Code is required").bind(Branch::getBranchCode, Branch::setBranchCode);
        binder.forField(branchName).asRequired("Branch Name is required").bind(Branch::getBranchName, Branch::setBranchName);
        binder.forField(address).asRequired("Address is required").bind(Branch::getAddress, Branch::setAddress);
        binder.forField(city).asRequired("City is required").bind(Branch::getCity, Branch::setCity);
        binder.forField(province).asRequired("Province is required").bind(Branch::getProvince, Branch::setProvince);

        // Read data into the form
        binder.readBean(branch);

        // Responsive grid form layout
        FormLayout formLayout = new FormLayout(branchCode, branchName, address, city, province);
        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("300px", 2));
        add(formLayout);

        // Action Buttons Setup
        Button saveButton = new Button("Save", e -> saveEntity());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", e -> close());

        getFooter().add(cancelButton, saveButton);
    }

    private void saveEntity() {
        try {
            // Write input entries straight into entity object variables
            binder.writeBean(branch);
            saveListener.onSave(branch);
            close();
        } catch (Exception e) {
            Notification.show("Please fix the validation errors before saving.", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}