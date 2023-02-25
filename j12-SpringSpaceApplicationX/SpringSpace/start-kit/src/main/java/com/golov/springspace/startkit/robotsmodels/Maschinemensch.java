package com.golov.springspace.startkit.robotsmodels;



import com.golov.springspace.infra.Tool;
import com.golov.springspace.startkit.robotsmodels.anotations.RobotAno;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RobotAno
public class Maschinemensch extends StandardRobot {
    public Maschinemensch(String name, String callSign, List<Tool> toolList) {
        super(name, callSign, toolList);
    }
}
