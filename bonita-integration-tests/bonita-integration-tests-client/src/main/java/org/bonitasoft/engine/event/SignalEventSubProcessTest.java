/**
 * Copyright (C) 2012-2013 BonitaSoft S.A.
 * BonitaSoft, 32 rue Gustave Eiffel - 38000 Grenoble
 * This library is free software; you can redistribute it and/or modify it under the terms
 * of the GNU Lesser General Public License as published by the Free Software Foundation
 * version 2.1 of the License.
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth
 * Floor, Boston, MA 02110-1301, USA.
 **/
package org.bonitasoft.engine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bonitasoft.engine.api.ProcessManagementAPI;
import org.bonitasoft.engine.bpm.data.DataInstance;
import org.bonitasoft.engine.bpm.data.DataNotFoundException;
import org.bonitasoft.engine.bpm.flownode.ActivityInstance;
import org.bonitasoft.engine.bpm.flownode.HumanTaskInstance;
import org.bonitasoft.engine.bpm.process.ProcessDefinition;
import org.bonitasoft.engine.bpm.process.ProcessInstance;
import org.bonitasoft.engine.bpm.process.ProcessInstanceState;
import org.bonitasoft.engine.bpm.process.SubProcessDefinition;
import org.bonitasoft.engine.expression.Expression;
import org.bonitasoft.engine.expression.ExpressionBuilder;
import org.bonitasoft.engine.test.TestStates;
import org.bonitasoft.engine.test.annotation.Cover;
import org.bonitasoft.engine.test.annotation.Cover.BPMNConcept;
import org.junit.Test;

/**
 * @author Baptiste Mesta
 * @author Elias Ricken de Medeiros
 * @author Celine Souchet
 */
public class SignalEventSubProcessTest extends WaitingEventTest {

    @Test
    @Cover(classes = { ProcessManagementAPI.class }, concept = BPMNConcept.EVENT_SUBPROCESS, keywords = { "expression context", "flow node container hierarchy" }, jira = "ENGINE-1848")
    public void evaluateExpressionsOnLoopUserTaskInSupProcess() throws Exception {
        final ProcessDefinition process = deployAndEnableProcessWithSignalEventSubProcess(false, true);
        final ProcessInstance processInstance = getProcessAPI().startProcess(process.getId());
        waitForUserTask(PARENT_PROCESS_USER_TASK_NAME, processInstance);
        checkNumberOfWaitingEvents(SUB_PROCESS_START_NAME, 1);

        // send signal to start event sub process
        getProcessAPI().sendSignal(SIGNAL_NAME);

        waitForFlowNodeInExecutingState(processInstance, SUB_PROCESS_NAME, false);
        final ActivityInstance subStep = waitForUserTask(SUB_PROCESS_USER_TASK_NAME, processInstance);

        final Map<Expression, Map<String, Serializable>> expressions = new HashMap<Expression, Map<String, Serializable>>();
        final String dataName = "content";
        expressions.put(new ExpressionBuilder().createDataExpression(dataName, String.class.getName()), new HashMap<String, Serializable>(0));
        final Map<String, Serializable> expressionResults = getProcessAPI().evaluateExpressionsOnActivityInstance(subStep.getId(), expressions);
        assertEquals("childActivityVar", expressionResults.get(dataName));

        disableAndDeleteProcess(process);
    }

    @Cover(classes = { SubProcessDefinition.class }, concept = BPMNConcept.EVENT_SUBPROCESS, keywords = { "event sub-process", "signal" }, jira = "ENGINE-536")
    @Test
    public void signalEventSubProcessTriggered() throws Exception {
        // given
        final ProcessDefinition process = deployAndEnableProcessWithSignalEventSubProcess(false, false);

        // when
        final ProcessInstance processInstance = getProcessAPI().startProcess(process.getId());
        final HumanTaskInstance step1 = waitForUserTask(PARENT_PROCESS_USER_TASK_NAME, processInstance);

        // then
        List<ActivityInstance> activities = getProcessAPI().getActivities(processInstance.getId(), 0, 10);
        assertThat(activities).as("should have 1 activity").hasSize(1);
        checkNumberOfWaitingEvents(SUB_PROCESS_START_NAME, 1);

        // when
        getProcessAPI().sendSignal(SIGNAL_NAME);
        waitForArchivedActivity(step1.getId(), TestStates.ABORTED);
        final ActivityInstance subStep = waitForUserTask(SUB_PROCESS_USER_TASK_NAME, processInstance);

        // then
        checkNumberOfWaitingEvents(SUB_PROCESS_START_NAME, 0);
        activities = getProcessAPI().getActivities(processInstance.getId(), 0, 10);
        assertThat(activities).as("should have 2 avtivities").hasSize(2);
        assertEquals(SUB_PROCESS_NAME, activities.get(0).getName());
        assertEquals(SUB_PROCESS_USER_TASK_NAME, activities.get(1).getName());

        // when
        final ProcessInstance subProcInst = getProcessAPI().getProcessInstance(subStep.getParentProcessInstanceId());
        assignAndExecuteStep(subStep, donaBenta.getId());
        waitForProcessToFinish(subProcInst);
        waitForProcessToBeInState(processInstance, ProcessInstanceState.ABORTED);

        // then
        // check that the transition wasn't taken
        checkWasntExecuted(processInstance, PARENT_END);

        disableAndDeleteProcess(process);
    }

    @Cover(classes = { SubProcessDefinition.class }, concept = BPMNConcept.EVENT_SUBPROCESS, keywords = { "event sub-process", "signal",
            "intermediateThrowEvent" }, jira = "ENGINE-1408")
    @Test
    public void signalEventSubProcessTriggeredWithIntermediateThrowEvent() throws Exception {
        final ProcessDefinition process = deployAndEnableProcessWithSignalEventSubProcess(true, false);
        final ProcessInstance processInstance = getProcessAPI().startProcess(process.getId());
        final ActivityInstance step1 = waitForUserTask(PARENT_PROCESS_USER_TASK_NAME, processInstance);
        checkNumberOfWaitingEvents(SUB_PROCESS_START_NAME, 1);

        assignAndExecuteStep(step1.getId(), donaBenta.getId());

        waitForUserTask(SUB_PROCESS_USER_TASK_NAME, processInstance);

        disableAndDeleteProcess(process);
    }

    @Cover(classes = { SubProcessDefinition.class }, concept = BPMNConcept.EVENT_SUBPROCESS, keywords = { "event sub-process", "signal" }, jira = "ENGINE-536")
    @Test
    public void signalEventSubProcessNotTriggered() throws Exception {
        final ProcessDefinition process = deployAndEnableProcessWithSignalEventSubProcess(false, false);
        final ProcessInstance processInstance = getProcessAPI().startProcess(process.getId());
        final ActivityInstance step1 = waitForUserTask(PARENT_PROCESS_USER_TASK_NAME, processInstance);
        final List<ActivityInstance> activities = getProcessAPI().getActivities(processInstance.getId(), 0, 10);
        assertEquals(1, activities.size());
        checkNumberOfWaitingEvents(SUB_PROCESS_START_NAME, 1);

        assignAndExecuteStep(step1, donaBenta.getId());

        waitForArchivedActivity(step1.getId(), TestStates.NORMAL_FINAL);
        waitForProcessToFinish(processInstance);

        // the parent process instance has completed, so no more waiting events are expected
        checkNumberOfWaitingEvents(SUB_PROCESS_START_NAME, 0);

        disableAndDeleteProcess(process);
    }

    @Cover(classes = { SubProcessDefinition.class }, concept = BPMNConcept.EVENT_SUBPROCESS, keywords = { "event sub-process", "signal" }, jira = "ENGINE-536")
    @Test
    public void createSeveralInstances() throws Exception {
        final ProcessDefinition process = deployAndEnableProcessWithSignalEventSubProcess(false, false);
        final ProcessInstance processInstance1 = getProcessAPI().startProcess(process.getId());
        final ProcessInstance processInstance2 = getProcessAPI().startProcess(process.getId());

        waitForUserTask(PARENT_PROCESS_USER_TASK_NAME, processInstance1);
        waitForUserTask(PARENT_PROCESS_USER_TASK_NAME, processInstance2);

        // send signal to start event sub processes: one signal must start the event sub-processes in the two process instances
        getProcessAPI().sendSignal(SIGNAL_NAME);

        waitForUserTask(SUB_PROCESS_USER_TASK_NAME, processInstance1);
        waitForUserTask(SUB_PROCESS_USER_TASK_NAME, processInstance2);
        Thread.sleep(50);

        disableAndDeleteProcess(process);
    }

    @Cover(classes = { SubProcessDefinition.class }, concept = BPMNConcept.EVENT_SUBPROCESS, keywords = { "event sub-process", "signal", "parent process data" }, jira = "ENGINE-536")
    @Test
    public void subProcessCanAccessParentData() throws Exception {
        final ProcessDefinition process = deployAndEnableProcessWithSignalEventSubProcess(false, true);
        final ProcessInstance processInstance = getProcessAPI().startProcess(process.getId());
        waitForUserTask(PARENT_PROCESS_USER_TASK_NAME, processInstance);

        getProcessAPI().sendSignal(SIGNAL_NAME);

        final ActivityInstance subStep = waitForUserTask(SUB_PROCESS_USER_TASK_NAME, processInstance);
        final ProcessInstance subProcInst = getProcessAPI().getProcessInstance(subStep.getParentProcessInstanceId());
        checkProcessDataInstance("count", subProcInst.getId(), 1);
        checkProcessDataInstance("content", subProcInst.getId(), "childVar");
        checkProcessDataInstance("value", subProcInst.getId(), 10.0);
        checkProcessDataInstance("content", processInstance.getId(), "parentVar");
        checkActivityDataInstance("content", subStep.getId(), "childActivityVar");

        assignAndExecuteStep(subStep, donaBenta.getId());
        waitForProcessToFinish(subProcInst);
        waitForProcessToFinish(processInstance, TestStates.ABORTED);

        disableAndDeleteProcess(process);
    }

    private void checkProcessDataInstance(final String dataName, final long processInstanceId, final Serializable expectedValue) throws DataNotFoundException {
        final DataInstance processDataInstance;
        processDataInstance = getProcessAPI().getProcessDataInstance(dataName, processInstanceId);
        assertEquals(expectedValue, processDataInstance.getValue());
    }

    private void checkActivityDataInstance(final String dataName, final long activityInstanceId, final Serializable expectedValue) throws DataNotFoundException {
        final DataInstance activityDataInstance;
        activityDataInstance = getProcessAPI().getActivityDataInstance(dataName, activityInstanceId);
        assertEquals(expectedValue, activityDataInstance.getValue());
    }

    @Cover(classes = { SubProcessDefinition.class }, concept = BPMNConcept.EVENT_SUBPROCESS, keywords = { "event sub-process", "signal", "call activity" }, jira = "ENGINE-536")
    @Test
    public void signalEventSubProcInsideTargetCallActivity() throws Exception {
        final ProcessDefinition targetProcess = deployAndEnableProcessWithSignalEventSubProcess(false, false);
        final ProcessDefinition callerProcess = deployAndEnableProcessWithCallActivity(targetProcess.getName(), targetProcess.getVersion());
        final ProcessInstance processInstance = getProcessAPI().startProcess(callerProcess.getId());
        final ActivityInstance step1 = waitForUserTask(PARENT_PROCESS_USER_TASK_NAME, processInstance);

        getProcessAPI().sendSignal(SIGNAL_NAME);

        final ActivityInstance subStep = waitForUserTask(SUB_PROCESS_USER_TASK_NAME, processInstance);
        final ProcessInstance calledProcInst = getProcessAPI().getProcessInstance(step1.getParentProcessInstanceId());
        final ProcessInstance subProcInst = getProcessAPI().getProcessInstance(subStep.getParentProcessInstanceId());

        waitForArchivedActivity(step1.getId(), TestStates.ABORTED);
        assignAndExecuteStep(subStep, donaBenta.getId());
        waitForProcessToFinish(subProcInst);
        waitForProcessToFinish(calledProcInst, TestStates.ABORTED);

        waitForUserTaskAndExecuteIt("step2", processInstance.getId(), donaBenta.getId());
        waitForProcessToFinish(processInstance);

        disableAndDeleteProcess(callerProcess.getId());
        disableAndDeleteProcess(targetProcess.getId());
    }
}