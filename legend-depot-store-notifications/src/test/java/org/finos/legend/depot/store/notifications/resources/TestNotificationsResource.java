//  Copyright 2021 Goldman Sachs
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

package org.finos.legend.depot.store.notifications.resources;

import org.finos.legend.depot.core.authorisation.api.AuthorisationProvider;
import org.finos.legend.depot.domain.project.ProjectData;
import org.finos.legend.depot.services.api.projects.ManageProjectsService;
import org.finos.legend.depot.services.api.projects.ProjectsService;
import org.finos.legend.depot.services.projects.ProjectsServiceImpl;
import org.finos.legend.depot.store.mongo.TestStoreMongo;
import org.finos.legend.depot.store.mongo.projects.ProjectsMongo;
import org.finos.legend.depot.store.notifications.api.Queue;
import org.finos.legend.depot.store.notifications.domain.MetadataNotification;
import org.finos.legend.depot.store.notifications.store.mongo.NotificationsMongo;
import org.finos.legend.depot.store.notifications.store.mongo.QueueMongo;
import org.junit.Assert;
import org.junit.Test;

import javax.inject.Provider;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;


public class TestNotificationsResource extends TestStoreMongo
{

    public static final String VERSION = "1.0.0";
    private NotificationsMongo eventsMongo = new NotificationsMongo(mongoProvider);
    private Queue queue = new QueueMongo(mongoProvider);
    private ManageProjectsService projectsService = new ProjectsServiceImpl(new ProjectsMongo(mongoProvider));

    @Test
    public void canRetrieveEventsByDate()
    {

        MetadataNotification event1 = new MetadataNotification("testproject1", "test.com", "test", VERSION);
        MetadataNotification event2 = new MetadataNotification("testproject2", "test.comm", "test", VERSION);
        MetadataNotification event3 = new MetadataNotification("testproject3", "test.org", "test", VERSION);
        MetadataNotification event4 = new MetadataNotification("testproject4", "org.test", "test", VERSION);

        LocalDateTime aPointInTime = LocalDateTime.parse("2019-01-01 10:00:00", NotificationsManagerResource.DATE_TIME_FORMATTER);
        eventsMongo.insert(event1.setLastUpdated(toDate(aPointInTime)));
        eventsMongo.insert(event2.setLastUpdated(toDate(aPointInTime.plusHours(1))));
        eventsMongo.insert(event3.setLastUpdated(toDate(aPointInTime.plusHours(2))));
        eventsMongo.insert(event4.setLastUpdated(toDate(aPointInTime.plusHours(2).plusMinutes(35))));
        NotificationsManagerResource resource = new NotificationsManagerResource(eventsMongo, queue, projectsService, new MockAuthorisationProvider(), null);

        List<MetadataNotification> allEvents = resource.getAllEvents(aPointInTime.minusDays(100).format(NotificationsManagerResource.DATE_TIME_FORMATTER), null);
        Assert.assertNotNull(allEvents);
        Assert.assertEquals(4, allEvents.size());

        LocalDateTime lunchTime = LocalDateTime.parse("2019-01-01 12:00:00", NotificationsManagerResource.DATE_TIME_FORMATTER);
        List<MetadataNotification> afterLunch = resource.getAllEvents(lunchTime.format(NotificationsManagerResource.DATE_TIME_FORMATTER), null);
        Assert.assertNotNull(afterLunch);
        Assert.assertEquals(2, afterLunch.size());
    }

    @Test
    public void refreshAll()
    {
        NotificationsManagerResource resource = new NotificationsManagerResource(eventsMongo, queue, projectsService, new MockAuthorisationProvider(), null);

        resource.queueRefreshAllEvent();


        LocalDateTime lunchTime = LocalDateTime.parse("2019-01-01 12:00:00", NotificationsManagerResource.DATE_TIME_FORMATTER);
        List<MetadataNotification> afterLunch = resource.getAllEvents(lunchTime.format(NotificationsManagerResource.DATE_TIME_FORMATTER), null);
        Assert.assertNotNull(afterLunch);
        Assert.assertEquals(1, resource.getAllEventsInQueue().size());
    }

    @Test
    public void canValidateCoordinates()
    {
        projectsService.createOrUpdate(new ProjectData("1","test.group","artifact"));
        Assert.assertEquals(1,projectsService.getAll().size());
        NotificationsManagerResource resource = new NotificationsManagerResource(eventsMongo, queue, projectsService, new MockAuthorisationProvider(), null);
        resource.queueEvent("1","test.group","artifact","1.0.0",5);
        Assert.assertEquals(1, queue.getAll().size());
        try
        {
            resource.queueEvent("2","test.group","artifact","1.0.0",5);
            Assert.fail();
        }
        catch (Exception e)
        {
            Assert.assertEquals(1, queue.getAll().size());
        }
    }

    private class MockAuthorisationProvider implements AuthorisationProvider
    {
        @Override
        public void authorise(Provider<Principal> principalProvider, String role)
        {
            role.equalsIgnoreCase("Notifications");
        }
    }
}
