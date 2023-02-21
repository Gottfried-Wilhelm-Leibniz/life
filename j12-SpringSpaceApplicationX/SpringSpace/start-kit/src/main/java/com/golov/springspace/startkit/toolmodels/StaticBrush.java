package com.golov.springspace.startkit.toolmodels;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class StaticBrush extends StandardTool {
    public StaticBrush() {
        super("StaticBrush");
    }
}
