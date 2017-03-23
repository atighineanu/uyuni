/**
 * Copyright (c) 2017 SUSE LLC
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */

package com.redhat.rhn.domain.image.test;

import com.redhat.rhn.domain.action.Action;
import com.redhat.rhn.domain.channel.Channel;
import com.redhat.rhn.domain.channel.test.ChannelFactoryTest;
import com.redhat.rhn.domain.image.DockerfileProfile;
import com.redhat.rhn.domain.image.ImageInfo;
import com.redhat.rhn.domain.image.ImageInfoCustomDataValue;
import com.redhat.rhn.domain.image.ImageInfoFactory;
import com.redhat.rhn.domain.image.ImagePackage;
import com.redhat.rhn.domain.image.ImageProfileFactory;
import com.redhat.rhn.domain.image.ImageStore;
import com.redhat.rhn.domain.image.ImageStoreFactory;
import com.redhat.rhn.domain.image.ProfileCustomDataValue;
import com.redhat.rhn.domain.org.CustomDataKey;
import com.redhat.rhn.domain.org.test.CustomDataKeyTest;
import com.redhat.rhn.domain.rhnpackage.Package;
import com.redhat.rhn.domain.rhnpackage.test.PackageTest;
import com.redhat.rhn.domain.server.InstalledProduct;
import com.redhat.rhn.domain.server.MinionServer;
import com.redhat.rhn.domain.server.test.MinionServerFactoryTest;
import com.redhat.rhn.domain.token.ActivationKeyFactory;
import com.redhat.rhn.domain.token.Token;
import com.redhat.rhn.domain.token.TokenFactory;
import com.redhat.rhn.manager.entitlement.EntitlementManager;
import com.redhat.rhn.manager.system.SystemManager;
import com.redhat.rhn.taskomatic.TaskomaticApi;
import com.redhat.rhn.testing.BaseTestCaseWithUser;
import com.redhat.rhn.testing.TestUtils;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit3.JUnit3Mockery;
import org.jmock.lib.concurrent.Synchroniser;
import org.jmock.lib.legacy.ClassImposteriser;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ImageInfoFactoryTest extends BaseTestCaseWithUser {

    private static final Mockery CONTEXT = new JUnit3Mockery() {{
        setThreadingPolicy(new Synchroniser());
    }};

    @Override
    public void setUp() throws Exception {
        super.setUp();
        CONTEXT.setImposteriser(ClassImposteriser.INSTANCE);

    }

    public final void testScheduleBuild() throws Exception {
        TaskomaticApi taskomaticMock = CONTEXT.mock(TaskomaticApi.class);
        ImageInfoFactory.setTaskomaticApi(taskomaticMock);

        CONTEXT.checking(new Expectations() { {
            allowing(taskomaticMock).scheduleActionExecution(with(any(Action.class)));
        } });

        MinionServer buildHost = MinionServerFactoryTest.createTestMinionServer(user);

        // Create test store
        ImageStore store = new ImageStore();
        store.setLabel("myregistry");
        store.setUri("registry.domain.top");
        store.setStoreType(
                ImageStoreFactory.lookupStoreTypeByLabel(ImageStore.TYPE_REGISTRY).get());
        store.setOrg(user.getOrg());
        ImageStoreFactory.save(store);

        // Create test token for profile
        Channel baseChannel = ChannelFactoryTest.createBaseChannel(user);
        Channel childChannel = ChannelFactoryTest.createTestChannel(user);
        Set<Channel> channels = new HashSet<>();
        channels.add(baseChannel);
        channels.add(childChannel);
        Token token = ActivationKeyFactory.createNewKey(user, "test-key").getToken();
        token.setChannels(channels);
        TokenFactory.save(token);

        // Create test profile
        DockerfileProfile profile = new DockerfileProfile();
        profile.setLabel("suma-3.1-base");
        profile.setOrg(user.getOrg());
        profile.setPath(
                "http://git.domain.top/dockerimages.git#mybranch:profiles/suma-3.1-base");
        profile.setToken(token);
        profile.setTargetStore(store);
        ImageProfileFactory.save(profile);

        try {
            // Should not be processed because the server is not a build host yet.
            ImageInfoFactory
                    .scheduleBuild(buildHost.getId(), "v1.0", profile, new Date(), user);
        }
        catch (IllegalArgumentException e) {
            assertEquals("Server is not a build host.", e.getMessage());
        }

        assertEquals(0, ImageInfoFactory.listImageInfos(user.getOrg()).size());

        SystemManager.entitleServer(buildHost, EntitlementManager.CONTAINER_BUILD_HOST);

        // Schedule
        ImageInfoFactory.scheduleBuild(buildHost.getId(), "v1.0", profile, new Date(),
                user);
        assertEquals(1, ImageInfoFactory.listImageInfos(user.getOrg()).size());
        ImageInfo info =
                ImageInfoFactory.lookupByName("suma-3.1-base", "v1.0", store.getId()).get();

        // Assertions
        assertEquals("suma-3.1-base", info.getName());
        assertEquals("v1.0", info.getVersion());
        assertEquals(buildHost, info.getBuildServer());
        assertEquals(buildHost.getServerArch(), info.getImageArch());
        assertEquals(profile, info.getProfile());
        assertEquals(store, info.getStore());
        assertEquals(user.getOrg(), info.getOrg());
        assertEquals(2, info.getChannels().size());
        assertTrue(info.getChannels().contains(baseChannel));
        assertTrue(info.getChannels().contains(childChannel));
        assertTrue(info.getCustomDataValues().isEmpty());

        // Add inspection data after build
        Package p = PackageTest.createTestPackage(user.getOrg());
        ImagePackage pkg = new ImagePackage();
        pkg.setInstallTime(new Date());
        pkg.setImageInfo(info);
        pkg.setArch(p.getPackageArch());
        pkg.setEvr(p.getPackageEvr());
        pkg.setName(p.getPackageName());
        info.setPackages(Collections.singleton(pkg));

        InstalledProduct prd = new InstalledProduct();
        prd.setName("SLES");
        prd.setVersion("12.1");
        prd.setArch(p.getPackageArch());
        prd.setBaseproduct(true);
        TestUtils.saveAndReload(prd);
        info.setInstalledProducts(Collections.singleton(prd));
        ImageInfoFactory.save(info);
        TestUtils.saveAndFlush(info);

        // Update values
        CustomDataKey key = CustomDataKeyTest.createTestCustomDataKey(user);
        ProfileCustomDataValue val = ImageProfileFactoryTest
                .createTestProfileCustomDataValue("Test value", user, key, profile);
        Set<ProfileCustomDataValue> cdvSet = new HashSet<>();
        cdvSet.add(val);
        profile.setCustomDataValues(cdvSet);
        TestUtils.saveAndFlush(profile);

        // Reschedule
        ImageInfoFactory.scheduleBuild(buildHost.getId(), "v1.0", profile, new Date(),
                user);

        // Image info should be reset
        assertEquals(1, ImageInfoFactory.listImageInfos(user.getOrg()).size());
        info = ImageInfoFactory.lookupByName("suma-3.1-base", "v1.0", store.getId()).get();

        assertEquals("suma-3.1-base", info.getName());
        assertEquals("v1.0", info.getVersion());
        assertEquals(buildHost, info.getBuildServer());
        assertEquals(buildHost.getServerArch(), info.getImageArch());
        assertEquals(profile, info.getProfile());
        assertEquals(store, info.getStore());
        assertEquals(user.getOrg(), info.getOrg());
        assertEquals(1, info.getCustomDataValues().size());
        assertEquals(2, info.getChannels().size());
        assertTrue(info.getChannels().contains(baseChannel));
        assertTrue(info.getChannels().contains(childChannel));
        ImageInfoCustomDataValue cdv = info.getCustomDataValues().iterator().next();
        assertEquals(key, cdv.getKey());
        assertEquals("Test value", cdv.getValue());
        assertTrue(info.getPackages().isEmpty());
        assertTrue(info.getInstalledProducts().isEmpty());

        // Test without a token
        profile.setToken(null);
        TestUtils.saveAndFlush(profile);

        // Schedule
        ImageInfoFactory.scheduleBuild(buildHost.getId(), "v2.0", profile, new Date(),
                user);

        // We should have two image infos with same labels but different versions
        List<ImageInfo> infoList = ImageInfoFactory.listImageInfos(user.getOrg());

        assertEquals(2, infoList.size());
        infoList.forEach(i -> assertEquals("suma-3.1-base", i.getName()));
        assertFalse(infoList.get(0).getVersion().equals(infoList.get(1).getVersion()));

        info = ImageInfoFactory.lookupByName("suma-3.1-base", "v2.0", store.getId()).get();

        // Assertions
        assertEquals(0, info.getChannels().size());
    }
}
