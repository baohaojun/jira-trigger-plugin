package com.ceilfors.jenkins.plugins.jirabuilder

import com.ceilfors.jenkins.plugins.jirabuilder.webhook.JiraWebHook
import com.gargoylesoftware.htmlunit.html.HtmlPage
import hudson.model.*
import org.jvnet.hudson.test.JenkinsRule

import java.util.concurrent.TimeUnit

import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat
/**
 * @author ceilfors
 */
class JenkinsRunner extends JenkinsRule {

    AbstractBuild buildShouldBeScheduled(String jobName) {
        def build = jiraBuilder.getLastScheduledBuild(5, TimeUnit.SECONDS)
        assertThat("Build is scheduled", build, is(not(nullValue())))
        assertThat("Last scheduled build should be for the job matched", build.project.name, is(jobName))
        return build
    }

    void buildShouldNotBeScheduled(String jobName) {
        def build = jiraBuilder.getLastScheduledBuild(5, TimeUnit.SECONDS)
        assertThat("Build is not scheduled", build, is(nullValue()))
    }

    private JiraBuilder getJiraBuilder() {
        instance.getInjector().getInstance(JiraBuilder)
    }

    private JiraWebHook getJiraWebHook() {
        instance.getActions().find { it instanceof JiraWebHook } as JiraWebHook
    }

    String getWebHookUrl() {
        return "${getURL().toString()}${jiraWebHook.urlName}/"
                .replace("localhost", "10.0.2.2") // vagrant
    }

    FreeStyleProject createJiraTriggeredProject(String name, String... parameters) {
        FreeStyleProject project = createFreeStyleProject(name)
        project.addProperty(new ParametersDefinitionProperty(parameters.collect {
            new StringParameterDefinition(it, "")
        }))

        JiraBuilderConfigurePage configPage = configure(name)
        configPage.activateJiraBuilderTrigger()
        configPage.save()

        assertThat(project.triggers.values(), hasItem(instanceOf(JiraBuilderTrigger)))
        return project
    }

    boolean buildTriggeredWithParameter(String jobName, Map<String, String> parameterMap) {
        def parametersAction = instance.getItemByFullName(jobName, AbstractProject).lastSuccessfulBuild.getAction(ParametersAction)
        parameterMap.each { key, value ->
            assertThat(parametersAction.getParameter(key), is(notNullValue()))
            assertThat(parametersAction.getParameter(key).value as String, is(value))
        }
        return true
    }

    def setJiraBuilderCommentPattern(String name, String commentPattern) {
        JiraBuilderConfigurePage configPage = configure(name)
        configPage.setCommentPattern(commentPattern)
        configPage.save()

        JiraBuilderTrigger jiraBuilderTrigger = instance.getItemByFullName(name, AbstractProject).getTrigger(JiraBuilderTrigger)
        assertThat(jiraBuilderTrigger.commentPattern, is(commentPattern))
    }

    JiraBuilderConfigurePage configure(String jobName) {
        HtmlPage htmlPage = createWebClient().goTo("job/$jobName/configure")
        return new JiraBuilderConfigurePage(htmlPage)
    }

    void addParameterMapping(String name, String jenkinsParameter, String jiraAttributePath) {
        JiraBuilderConfigurePage configPage = configure(name)
        configPage.addParameterMapping(jenkinsParameter, jiraAttributePath)
        configPage.save()

        JiraBuilderTrigger jiraBuilderTrigger = instance.getItemByFullName(name, AbstractProject).getTrigger(JiraBuilderTrigger)
        assertThat("Parameter mapping is added", jiraBuilderTrigger.parameterMappings.size(), is(1))
        assertThat(jiraBuilderTrigger.parameterMappings.first().jenkinsParameter, is(jenkinsParameter))
        assertThat(jiraBuilderTrigger.parameterMappings.first().jiraAttributePath, is(jiraAttributePath))
    }
}
