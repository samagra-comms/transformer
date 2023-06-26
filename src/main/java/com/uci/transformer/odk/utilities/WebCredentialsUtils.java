package com.uci.transformer.odk.utilities;

import android.net.Uri;
import com.uci.transformer.odk.openrosa.HttpCredentials;
import com.uci.transformer.odk.openrosa.HttpCredentialsInterface;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class WebCredentialsUtils {

    @Value("${odk.username")
    public String username;

    @Value("${odk.password")
    public String password;

    private static final Map<String, HttpCredentialsInterface> HOST_CREDENTIALS = new HashMap<>();

    public void saveCredentials(@NonNull String url, @NonNull String username, @NonNull String password) {
        if (username.isEmpty()) {
            return;
        }

        String host = Uri.parse(url).getHost();
        HOST_CREDENTIALS.put(host, new HttpCredentials(username, password));
    }

    public void saveCredentialsPreferences(String userName, String password) {

    }

    /**
     * Forgets the temporary credentials saved in memory for a particular host. This is used when an
     * activity that does some work requiring authentication is called with intent extras specifying
     * credentials. Once the work is done, the temporary credentials are cleared so that different
     * ones can be used on a later request.
     *
     * TODO: is this necessary in all cases it's used? Maybe it's needed if we want to be able to do
     * an authenticated call followed by an anonymous one but even then, can't we pass in null
     * username and password if the intent extras aren't set?
     */
    public void clearCredentials(@NonNull String url) {
        if (url.isEmpty()) {
            return;
        }

        String host = Uri.parse(url).getHost();
        if (host != null) {
            HOST_CREDENTIALS.remove(host);
        }
    }

    static void clearAllCredentials() {
        HOST_CREDENTIALS.clear();
    }

    public URI getServerUrlFromPreferences() throws MalformedURLException, URISyntaxException {
        String downloadUrl = System.getenv("ODK_URL");
        log.info("ODK URL ::" + downloadUrl);
        URL url = new URL(downloadUrl);
        return url.toURI();
    }

    public String getPasswordFromPreferences() {
        return System.getenv("ODK_PASS") ;
    }

    public String getUserNameFromPreferences() {
        return System.getenv("ODK_USER") ;
    }

    /**
     * Returns a credentials object from the url
     *
     * @param url to find the credentials object
     * @return either null or an instance of HttpCredentialsInterface
     */
    public @Nullable
    HttpCredentialsInterface getCredentials(@NonNull URI url) {
        String host = url.getHost();
        URI serverPrefsUrl = null;
        try {
            serverPrefsUrl = getServerUrlFromPreferences();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        String prefsServerHost = (serverPrefsUrl == null) ? null : serverPrefsUrl.getHost();

        // URL host is the same as the host in preferences
        if (prefsServerHost != null && prefsServerHost.equalsIgnoreCase(host)) {
            // Use the temporary credentials if they exist, otherwise use the credentials saved to preferences
            if (HOST_CREDENTIALS.containsKey(host)) {
                return HOST_CREDENTIALS.get(host);
            } else {
                return new HttpCredentials(getUserNameFromPreferences(), getPasswordFromPreferences());
            }
        } else {
            return HOST_CREDENTIALS.get(host);
        }
    }

}
