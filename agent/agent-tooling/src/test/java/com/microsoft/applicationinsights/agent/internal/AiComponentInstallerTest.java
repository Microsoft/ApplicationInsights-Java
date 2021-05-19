package com.microsoft.applicationinsights.agent.internal;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class AiComponentInstallerTest {

    @Test
    public void jmxObjectNameCreation() {
        assertEquals("java.lang /Threading /", AiComponentInstaller.getJmxDisplayName("java.lang:type=Threading"));
        assertEquals("java.nio /BufferPool /direct /", AiComponentInstaller.getJmxDisplayName("java.nio:type=BufferPool,name=direct"));
        assertEquals("java.lang /GarbageCollector /PS MarkSweep /", AiComponentInstaller.getJmxDisplayName("java.lang:type=GarbageCollector,name=PS MarkSweep"));
        assertEquals("Catalina /RequestProcessor /\"http-nio-8080\" /HttpRequest1 /", AiComponentInstaller.getJmxDisplayName("Catalina:type=RequestProcessor,worker=\"http-nio-8080\",name=HttpRequest1"));
        assertEquals("Catalina /Servlet ///localhost/ /default /none /none /", AiComponentInstaller.getJmxDisplayName("Catalina:j2eeType=Servlet,WebModule=//localhost/,name=default,J2EEApplication=none,J2EEServer=none"));
        assertEquals("tomcat.jdbc /\"jdbc/storage\" // /Catalina /ConnectionPool /localhost /org.apache.tomcat.jdbc.pool.DataSource /", AiComponentInstaller.getJmxDisplayName("tomcat.jdbc:name=\"jdbc/storage\",context=/,engine=Catalina,type=ConnectionPool,host=localhost,class=org.apache.tomcat.jdbc.pool.DataSource"));
        assertEquals("tomcat.jdbc /\"jdbc/=,.25.7=storage\" // /Catalina /ConnectionPool /localhost /org.apache.tomcat.jdbc.pool.DataSource /", AiComponentInstaller.getJmxDisplayName("tomcat.jdbc:name=\"jdbc/=,.25.7=storage\",context=/,engine=Catalina,type=ConnectionPool,host=localhost,class=org.apache.tomcat.jdbc.pool.DataSource"));
    }
}
