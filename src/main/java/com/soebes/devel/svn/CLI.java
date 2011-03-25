package com.soebes.devel.svn;

import java.io.File;
import java.util.Arrays;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.ISVNHostOptionsProvider;
import org.tmatesoft.svn.core.internal.wc.SVNCompositeConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNWCProperties;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

public class CLI {

    static {
        setupLibrary();
    }
    private static void setupLibrary() {
        /*
         * For using over http:// and https://
         */
        DAVRepositoryFactory.setup();
        /*
         * For using over svn:// and svn+xxx://
         */
        SVNRepositoryFactoryImpl.setup();

        /*
         * For using over file:///
         */
        FSRepositoryFactory.setup();
    }


    public static void main(String[] args) {
        try {
            readPasswordStorage();
            testPassword();
        } catch (SVNException e) {
            System.err.println("SVNException:" + e.getMessage());
        }
    }

    public static void readPasswordStorage() throws SVNException {
        File configDirectory = SVNWCUtil.getDefaultConfigurationDirectory();
        File authCacheArea = new File(configDirectory, "auth");
        File passwordCacheArea = new File(authCacheArea, "svn.simple");

        if (!passwordCacheArea.isDirectory()) {
            System.out.println("Password cache doesn't exist");
            return;
        }

        File[] realmCacheFiles = SVNFileListUtil.listFiles(passwordCacheArea);

        for (int i = 0; i < realmCacheFiles.length; i++) {
            File realmCacheFile = realmCacheFiles[i];
            SVNWCProperties properties = new SVNWCProperties(realmCacheFile, null);
            SVNProperties realmCache = properties.asMap();
            String realm = SVNPropertyValue.getPropertyAsString(realmCache.getSVNPropertyValue("svn:realmstring"));
            String passwordStorageType = SVNPropertyValue.getPropertyAsString(realmCache.getSVNPropertyValue("passtype"));
            System.out.println("Realm cache: " + realmCacheFile.getAbsolutePath());
            System.out.println("Realm: " + realm);
            System.out.println("Passtype: " + passwordStorageType);
        }
    }

    public static void testPassword() throws SVNException {
        DefaultSVNAuthenticationManager manager = (DefaultSVNAuthenticationManager) SVNWCUtil.createDefaultAuthenticationManager();

        // API to work with ~/.subversion/config
        DefaultSVNOptions defaultOptions = manager.getDefaultOptions();

        // API to work with ~/.subversion/servers
        ISVNHostOptionsProvider hostOptionsProvider = manager.getHostOptionsProvider();

        // 1. Get password storage types:
        String[] passwordStorageTypes = defaultOptions.getPasswordStorageTypes();
        System.out.println("passwordStorageTypes:" + Arrays.toString(passwordStorageTypes));

        // Be careful: SVNKit default password storage types != Subversion default password storage types
        // SVNKit doesn't yet support 'kwallet', so this type won't be listed if ~/.subversion/config has no option
        // [auth] password-stores = XXX
        // (or this option is commented)

        // To workaround that you may try do the following:
        File configDirectory = SVNWCUtil.getDefaultConfigurationDirectory();

        // Ensure all configuration files exist
        SVNConfigFile.createDefaultConfiguration(configDirectory);
        SVNConfigFile userConfig = new SVNConfigFile(new File(configDirectory, "config"));
        SVNConfigFile systemConfig = new SVNConfigFile(new File(SVNFileUtil.getSystemConfigurationDirectory(), "config"));
        SVNCompositeConfigFile configFile = new SVNCompositeConfigFile(systemConfig, userConfig);
        String passwordStoresValue = configFile.getPropertyValue("auth", "password-stores");

        System.out.println("passwordStoresValue:" + passwordStoresValue);
        // Then you may parse passwordStoresValue or use our API.
        if (passwordStoresValue == null) {
            passwordStorageTypes = new String[]{"gnome-keyring", "kwallet", "keychain", "windows-cryptoapi"};
        } else {
            passwordStorageTypes = defaultOptions.getPasswordStorageTypes();
        }
        System.out.println("Array:" + Arrays.toString(passwordStorageTypes));

        // 2. Get necessary options from ~/.subversion/servers with fallback to ~/.subversion/config options.

        SVNURL url = SVNURL.parseURIEncoded("https://svn.schlund.de/svn/PFX/");
        System.out.println("isAuthStorageEnabled():" + hostOptionsProvider.getHostOptions(url).isAuthStorageEnabled());
        System.out.println("isStorePasswords():"  + hostOptionsProvider.getHostOptions(url).isStorePasswords());
        System.out.println("isStorePlainTextPasswords():" + hostOptionsProvider.getHostOptions(url).isStorePlainTextPasswords(null, null));
    }
}
