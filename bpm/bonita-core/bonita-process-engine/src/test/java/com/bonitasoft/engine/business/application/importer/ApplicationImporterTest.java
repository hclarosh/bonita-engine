/*******************************************************************************
 * Copyright (C) 2014 BonitaSoft S.A.
 * BonitaSoft is a trademark of BonitaSoft SA.
 * This software file is BONITASOFT CONFIDENTIAL. Not For Distribution.
 * For commercial licensing information, contact:
 * BonitaSoft, 32 rue Gustave Eiffel – 38000 Grenoble
 * or BonitaSoft US, 51 Federal Street, Suite 305, San Francisco, CA 94107
 ******************************************************************************/

package com.bonitasoft.engine.business.application.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.Arrays;
import java.util.List;

import org.bonitasoft.engine.api.ImportError;
import org.bonitasoft.engine.api.ImportStatus;
import org.bonitasoft.engine.commons.exceptions.SObjectCreationException;
import org.bonitasoft.engine.commons.exceptions.SObjectNotFoundException;
import org.bonitasoft.engine.exception.ExecutionException;
import org.bonitasoft.engine.recorder.model.EntityUpdateDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.bonitasoft.engine.business.application.ApplicationService;
import com.bonitasoft.engine.business.application.converter.ApplicationNodeConverter;
import com.bonitasoft.engine.business.application.model.SApplication;
import com.bonitasoft.engine.business.application.model.SApplicationPage;
import com.bonitasoft.engine.business.application.model.builder.impl.SApplicationFields;
import com.bonitasoft.engine.business.application.xml.ApplicationMenuNode;
import com.bonitasoft.engine.business.application.xml.ApplicationNode;
import com.bonitasoft.engine.business.application.xml.ApplicationPageNode;

@RunWith(MockitoJUnitRunner.class)
public class ApplicationImporterTest {

    @Mock
    private ApplicationService applicationService;

    @Mock
    private ApplicationImportStrategy strategy;

    @Mock
    private ApplicationNodeConverter applicationNodeConverter;

    @Mock
    private ApplicationPageImporter applicationPageImporter;

    @Mock
    private ApplicationMenuImporter applicationMenuImporter;

    @InjectMocks
    private ApplicationImporter applicationImporter;

    @Test
    public void importApplication_should_create_application_import_pages_and_menus_and_return_status() throws Exception {
        //given
        long createdBy = 5L;
        SApplication app = mock(SApplication.class);

        ImportResult importResult = mock(ImportResult.class);
        given(importResult.getApplication()).willReturn(app);
        ImportStatus importStatus = mock(ImportStatus.class);
        given(importResult.getImportStatus()).willReturn(importStatus);

        ApplicationPageNode pageNode1 = mock(ApplicationPageNode.class);
        ApplicationPageNode pageNode2 = mock(ApplicationPageNode.class);

        ApplicationMenuNode menu1 = new ApplicationMenuNode();
        ApplicationMenuNode menu2 = new ApplicationMenuNode();

        ApplicationNode applicationNode = new ApplicationNode();
        applicationNode.addApplicationPage(pageNode1);
        applicationNode.addApplicationPage(pageNode2);
        applicationNode.addApplicationMenu(menu1);
        applicationNode.addApplicationMenu(menu2);
        applicationNode.setHomePage("home");
        applicationNode.setToken("app");
        given(applicationNodeConverter.toSApplication(applicationNode, createdBy)).willReturn(importResult);

        long homePageId = 222L;
        SApplicationPage applicationPage = mock(SApplicationPage.class);
        given(applicationPage.getId()).willReturn(homePageId);
        given(applicationService.getApplicationPage("app", "home")).willReturn(applicationPage);

        ImportError errorPage = mock(ImportError.class);
        List<ImportError> errorsMenu = Arrays.asList(mock(ImportError.class));
        given(applicationPageImporter.importApplicationPage(pageNode1, app)).willReturn(errorPage);
        given(applicationMenuImporter.importApplicationMenu(menu1, app, null)).willReturn(errorsMenu);

        given(applicationService.createApplication(app)).willReturn(app);

        //when
        ImportStatus retrievedStatus = applicationImporter.importApplication(applicationNode, createdBy);

        //then
        //create application
        assertThat(retrievedStatus).isEqualTo(importResult.getImportStatus());
        verify(applicationService, times(1)).createApplication(app);
        verifyZeroInteractions(strategy);

        //add pages
        verify(applicationPageImporter, times(1)).importApplicationPage(pageNode1, app);
        verify(applicationPageImporter, times(1)).importApplicationPage(pageNode2, app);
        verify(importStatus, times(1)).addError(errorPage);

        //add menus
        verify(applicationMenuImporter, times(1)).importApplicationMenu(menu1, app, null);
        verify(applicationMenuImporter, times(1)).importApplicationMenu(menu2, app, null);
        verify(importStatus, times(1)).addErrors(errorsMenu);
        verify(importStatus, never()).addError(null);

        //set home page
        ArgumentCaptor<EntityUpdateDescriptor> updateCaptor = ArgumentCaptor.forClass(EntityUpdateDescriptor.class);
        verify(applicationService, times(1)).updateApplication(eq(app), updateCaptor.capture());
        EntityUpdateDescriptor updateDescriptor = updateCaptor.getValue();
        assertThat(updateDescriptor.getFields().get(SApplicationFields.HOME_PAGE_ID)).isEqualTo(homePageId);
    }

    @Test
    public void importApplication_should_not_set_home_page_when_applicationNod_does_not_have_home_page() throws Exception {
        //given
        long createdBy = 5L;
        SApplication app = mock(SApplication.class);

        ImportResult importResult = mock(ImportResult.class);
        given(importResult.getApplication()).willReturn(app);
        ImportStatus importStatus = mock(ImportStatus.class);
        given(importResult.getImportStatus()).willReturn(importStatus);

        ApplicationNode applicationNode = new ApplicationNode();
        applicationNode.setToken("app");
        given(applicationNodeConverter.toSApplication(applicationNode, createdBy)).willReturn(importResult);
        given(applicationService.createApplication(app)).willReturn(app);

        //when
        applicationImporter.importApplication(applicationNode, createdBy);

        //then
        //set home page
        verify(applicationService, never()).updateApplication(any(SApplication.class), any(EntityUpdateDescriptor.class));
    }

    @Test
    public void importApplication_should_add_error_when_home_page_is_not_found() throws Exception {
        //given
        long createdBy = 5L;
        SApplication app = mock(SApplication.class);

        ImportResult importResult = mock(ImportResult.class);
        given(importResult.getApplication()).willReturn(app);
        ImportStatus importStatus = mock(ImportStatus.class);
        given(importResult.getImportStatus()).willReturn(importStatus);

        ApplicationNode applicationNode = new ApplicationNode();
        applicationNode.setToken("app");
        applicationNode.setHomePage("home");

        given(applicationNodeConverter.toSApplication(applicationNode, createdBy)).willReturn(importResult);
        given(applicationService.createApplication(app)).willReturn(app);

        given(applicationService.getApplicationPage("app", "home")).willThrow(new SObjectNotFoundException(""));

        //when
        applicationImporter.importApplication(applicationNode, createdBy);

        //then
        //set home page
        verify(applicationService, never()).updateApplication(any(SApplication.class), any(EntityUpdateDescriptor.class));
        verify(importStatus, times(1)).addError(new ImportError("home", ImportError.Type.APPLICATION_PAGE));
    }

    @Test
    public void importApplication_should_call_importStrategy_when_application_already_exists() throws Exception {
        //given
        long createdBy = 5L;
        SApplication appToBeImported = mock(SApplication.class);
        given(appToBeImported.getToken()).willReturn("application");

        ImportResult importResult = mock(ImportResult.class);
        given(importResult.getApplication()).willReturn(appToBeImported);

        SApplication appInConflict = mock(SApplication.class);

        ApplicationNode applicationNode = mock(ApplicationNode.class);
        given(applicationNodeConverter.toSApplication(applicationNode, createdBy)).willReturn(importResult);
        given(applicationService.getApplicationByToken("application")).willReturn(appInConflict);

        //when
        applicationImporter.importApplication(applicationNode, createdBy);

        //then
        verify(applicationService, times(1)).createApplication(appToBeImported);
        verify(strategy, times(1)).whenApplicationExists(appInConflict, appToBeImported);
    }

    @Test(expected = ExecutionException.class)
    public void importApplication_should_throw_ExecutionException_when_application_service_throws_exception() throws Exception {
        //given
        long createdBy = 5L;
        SApplication app1 = mock(SApplication.class);

        ImportResult importResult = mock(ImportResult.class);
        given(importResult.getApplication()).willReturn(app1);

        ApplicationNode applicationNode = mock(ApplicationNode.class);
        given(applicationNodeConverter.toSApplication(applicationNode, createdBy)).willReturn(importResult);

        given(applicationService.createApplication(app1)).willThrow(new SObjectCreationException(""));

        //when
        applicationImporter.importApplication(applicationNode, createdBy);

        //then exception
    }
}
