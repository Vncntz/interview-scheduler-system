package com.company.iss.dashboard.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;

import java.util.List;

@Tag("iss-dashboard-chart")
@JsModule("./dashboard-chart.js")
@NpmPackage(value = "chart.js", version = "4.5.1")
public class DashboardChart extends Component implements HasSize {

    public DashboardChart(String type, List<String> labels, List<Long> values, String accessibleLabel) {
        setWidthFull();
        getElement().executeJs(
                "this.setData($0, $1, $2, $3)",
                type,
                labels,
                values,
                accessibleLabel
        );
    }
}
