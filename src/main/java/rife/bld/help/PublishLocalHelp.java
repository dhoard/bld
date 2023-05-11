/*
 * Copyright 2001-2023 Geert Bevin (gbevin[remove] at uwyn dot com)
 * Licensed under the Apache License, Version 2.0 (the "License")
 */
package rife.bld.help;

import rife.bld.CommandHelp;
import rife.tools.StringUtils;

/**
 * Provides help for the publish command.
 *
 * @author Geert Bevin (gbevin[remove] at uwyn dot com)
 * @since 1.5.7
 */
public class PublishHelp implements CommandHelp {
    public String getSummary() {
        return "Publishes the artifacts of your project";
    }

    public String getDescription(String topic) {
        return StringUtils.replace("""
            Publishes the artifacts of the project to the publication
            repository.
            The standard publish command will automatically also execute
            the jar, jar-sources and jar-javadoc commands beforehand.
                        
            Usage : ${topic}""", "${topic}", topic);
    }
}