package com.bonitasoft.engine.api.impl;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.bonitasoft.engine.api.impl.NodeConfiguration;
import org.bonitasoft.engine.builder.BuilderFactory;
import org.bonitasoft.engine.exception.UpdateException;
import org.bonitasoft.engine.execution.work.RestartException;
import org.bonitasoft.engine.execution.work.TenantRestartHandler;
import org.bonitasoft.engine.platform.PlatformService;
import org.bonitasoft.engine.platform.STenantNotFoundException;
import org.bonitasoft.engine.platform.model.STenant;
import org.bonitasoft.engine.platform.model.builder.STenantBuilderFactory;
import org.bonitasoft.engine.platform.model.impl.STenantImpl;
import org.bonitasoft.engine.recorder.model.EntityUpdateDescriptor;
import org.bonitasoft.engine.scheduler.SchedulerService;
import org.bonitasoft.engine.session.SessionService;
import org.bonitasoft.engine.work.SWorkException;
import org.bonitasoft.engine.work.WorkService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.bonitasoft.engine.service.BroadcastService;
import com.bonitasoft.engine.service.PlatformServiceAccessor;
import com.bonitasoft.engine.service.TenantServiceAccessor;
import com.bonitasoft.engine.service.impl.BroadcastServiceLocal;

public class TenantManagementAPIExtTest {

    private TenantManagementAPIExt tenantManagementAPI;

    private PlatformService platformService;

    private SchedulerService schedulerService;

    private PlatformServiceAccessor platformServiceAccessor;

    private long tenantId;

    private STenantImpl sTenant;

    private SessionService sessionService;

    private WorkService workService;

    private TenantServiceAccessor tenantServiceAccessor;

    private NodeConfiguration nodeConfiguration;

    private TenantRestartHandler tenantRestartHandler1;

    private TenantRestartHandler tenantRestartHandler2;

    private final BroadcastService broadcastService = new BroadcastServiceLocal();

    @Before
    public void before() throws Exception {
        tenantManagementAPI = spy(new TenantManagementAPIExt());
        platformService = mock(PlatformService.class);
        schedulerService = mock(SchedulerService.class);
        sessionService = mock(SessionService.class);
        platformServiceAccessor = mock(PlatformServiceAccessor.class);
        tenantServiceAccessor = mock(TenantServiceAccessor.class);
        nodeConfiguration = mock(NodeConfiguration.class);
        workService = mock(WorkService.class);
        tenantRestartHandler1 = mock(TenantRestartHandler.class);
        tenantRestartHandler2 = mock(TenantRestartHandler.class);
        doReturn(platformServiceAccessor).when(tenantManagementAPI).getPlatformAccessorNoException();
        doReturn(new PauseServices(tenantId) {

            private static final long serialVersionUID = 1L;

            @Override
            PlatformServiceAccessor getPlatformAccessor() {
                return platformServiceAccessor;
            }
        }).when(tenantManagementAPI).createPauseServicesTask(anyLong());
        doReturn(new ResumeServices(tenantId) {

            private static final long serialVersionUID = 1L;

            @Override
            PlatformServiceAccessor getPlatformAccessor() {
                return platformServiceAccessor;
            }
        }).when(tenantManagementAPI).createResumeServicesTask(anyLong());
        doReturn(broadcastService).when(platformServiceAccessor).getBroadcastService();
        doReturn(schedulerService).when(platformServiceAccessor).getSchedulerService();
        doReturn(platformService).when(platformServiceAccessor).getPlatformService();
        doReturn(nodeConfiguration).when(platformServiceAccessor).getPlaformConfiguration();
        doReturn(sessionService).when(platformServiceAccessor).getSessionService();
        doReturn(tenantServiceAccessor).when(platformServiceAccessor).getTenantServiceAccessor(Mockito.anyLong());
        doReturn(Arrays.asList(tenantRestartHandler1, tenantRestartHandler2)).when(nodeConfiguration).getTenantRestartHandlers();

        tenantId = 17;
        doReturn(tenantId).when(tenantManagementAPI).getTenantId();
        doReturn(workService).when(tenantServiceAccessor).getWorkService();
        sTenant = new STenantImpl("myTenant", "john", 123456789, STenant.PAUSED, false);
        when(platformService.getTenant(tenantId)).thenReturn(sTenant);
    }

    @Test
    public void setMaintenanceModeToMAINTENANCEShouldPauseWorkService() throws Exception {
        whenTenantIsInState(STenant.ACTIVATED);

        // given a tenant moved to maintenance mode
        tenantManagementAPI.pause();

        // then his work service should be pause
        verify(workService).pause();
    }

    @Test
    public void setMaintenanceModeToAVAILLABLEShouldResumeWorkService() throws Exception {

        // given a tenant moved to available mode
        tenantManagementAPI.resume();

        // then his work service should be resumed
        verify(workService).resume();
    }

    @Test(expected = UpdateException.class)
    public void should_setMaintenanceMode_to_AVAILLABLE_throw_exception_when_workservice_fail() throws Exception {
        doThrow(SWorkException.class).when(workService).resume();

        // given a tenant moved to available mode
        tenantManagementAPI.resume();
    }

    @Test
    public void should_setMaintenanceMode_to_AVAILLABLE_restart_elements() throws Exception {

        // given a tenant moved to available mode
        tenantManagementAPI.resume();

        // then elements must be restarted
        verify(tenantRestartHandler1, times(1)).handleRestart(platformServiceAccessor, tenantServiceAccessor);
        verify(tenantRestartHandler2, times(1)).handleRestart(platformServiceAccessor, tenantServiceAccessor);
    }

    @Test(expected = UpdateException.class)
    public void should_setMaintenanceMode_to_AVAILLABLE__throw_exception_when_RestartHandler_fail() throws Exception {
        doThrow(RestartException.class).when(tenantRestartHandler2).handleRestart(platformServiceAccessor, tenantServiceAccessor);

        // given a tenant moved to available mode
        tenantManagementAPI.resume();
    }

    @Test
    public void setTenantMaintenanceModeShouldUpdateMaintenanceField() throws Exception {
        whenTenantIsInState(STenant.ACTIVATED);

        tenantManagementAPI.pause();

        EntityUpdateDescriptor entityUpdateDescriptor = new EntityUpdateDescriptor();
        String inMaintenanceKey = BuilderFactory.get(STenantBuilderFactory.class).getStatusKey();
        entityUpdateDescriptor.addField(inMaintenanceKey, STenant.PAUSED);

        verify(platformService).updateTenant(sTenant, entityUpdateDescriptor);
    }

    @Test
    public void should_setMaintenanceMode_to_MAINTENANCE_pause_jobs() throws Exception {
        whenTenantIsInState(STenant.ACTIVATED);

        tenantManagementAPI.pause();

        verify(schedulerService).pauseJobs(tenantId);
    }

    @Test
    public void should_setMaintenanceMode_to_AVAILABLE_pause_jobs() throws Exception {
        tenantManagementAPI.resume();

        verify(schedulerService).resumeJobs(tenantId);
    }

    @Test
    public void should_setMaintenanceMode_to_MAINTENANCE_delete_sessions() throws Exception {
        whenTenantIsInState(STenant.ACTIVATED);

        tenantManagementAPI.pause();

        verify(sessionService).deleteSessionsOfTenantExceptTechnicalUser(tenantId);
    }

    @Test
    public void should_setMaintenanceMode_to_AVAILABLE_delete_sessions() throws Exception {
        tenantManagementAPI.resume();

        verify(sessionService, times(0)).deleteSessionsOfTenantExceptTechnicalUser(tenantId);
    }

    @Test
    public void setTenantMaintenanceModeShouldHaveAnnotationAvailableOnMaintenanceTenant() throws Exception {
        assertTrue("Annotation @AvailableWhenTenantIsPaused should be present on API method TenantManagementAPIExt",
                TenantManagementAPIExt.class.isAnnotationPresent(AvailableWhenTenantIsPaused.class));
    }

    @Test
    public void loginShouldHaveAnnotationAvailableOnMaintenanceTenant() throws Exception {
        // given:
        Method method = LoginAPIExt.class.getMethod("login", long.class, String.class, String.class);

        // then:
        assertTrue("Annotation @AvailableOnMaintenanceTenant should be present on API method LoginAPIExt.login(long, String, String)",
                method.isAnnotationPresent(AvailableWhenTenantIsPaused.class));
    }

    @Test
    public void loginWithTenantIdShouldHaveAnnotationAvailableOnMaintenanceTenant() throws Exception {
        // given:
        Method method = LoginAPIExt.class.getMethod("login", String.class, String.class);

        // then:
        assertTrue("Annotation @AvailableOnMaintenanceTenant should be present on API method LoginAPIExt.login(String, String)",
                method.isAnnotationPresent(AvailableWhenTenantIsPaused.class));
    }

    @Test(expected = UpdateException.class)
    public void should_pause_on_a_paused_tenant_throw_update_exception() throws Exception {
        whenTenantIsInState(STenant.PAUSED);

        tenantManagementAPI.pause();
    }

    @Test(expected = UpdateException.class)
    public void should_pause_on_a_deactivated_tenant_throw_update_exception() throws Exception {
        whenTenantIsInState(STenant.DEACTIVATED);

        tenantManagementAPI.pause();
    }

    @Test(expected = UpdateException.class)
    public void should_resume_on_a_paused_tenant_throw_update_exception() throws Exception {
        whenTenantIsInState(STenant.ACTIVATED);

        tenantManagementAPI.resume();
    }

    @Test(expected = UpdateException.class)
    public void should_resume_on_a_deactivated_tenant_throw_update_exception() throws Exception {
        whenTenantIsInState(STenant.DEACTIVATED);

        tenantManagementAPI.resume();
    }

    private void whenTenantIsInState(final String status) throws STenantNotFoundException {
        sTenant = new STenantImpl("myTenant", "john", 123456789, status, false);
        when(platformService.getTenant(tenantId)).thenReturn(sTenant);
    }

    @Test(expected = UpdateException.class)
    public void should_pause_on_a_unexisting_tenant_throw_update_exception() throws Exception {
        doThrow(STenantNotFoundException.class).when(platformService).getTenant(tenantId);

        tenantManagementAPI.resume();
    }

}