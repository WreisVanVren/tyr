package org.xstefank.ci;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.xml.bind.DatatypeConverter;
import org.jboss.logging.Logger;
import org.xstefank.model.TyrProperties;
import org.xstefank.model.Utils;
import org.xstefank.model.json.triggerbuild.BuildJson;

public class TeamCityCI implements ContinuousIntegration {

    public static final String NAME = "TeamCity";

    public static final String HOST_PROPERTY = "teamcity.host";
    public static final String PORT_PROPERTY = "teamcity.port";
    public static final String USER_PROPERTY = "teamcity.user";
    public static final String PASSWORD_PROPERTY = "teamcity.password";
    public static final String BUILD_CONFIG = "teamcity.branch.mapping";

    private static final Logger log = Logger.getLogger(TeamCityCI.class);

    private String baseUrl;
    private String encryptedCredentials;
    private Map<String, String> branchMappings;

    @Override
    public void init() {
        this.baseUrl = getBaseUrl(TyrProperties.getProperty(HOST_PROPERTY),
                TyrProperties.getIntProperty(PORT_PROPERTY));

        this.encryptedCredentials = encryptCredentials(TyrProperties.getProperty(USER_PROPERTY),
                TyrProperties.getProperty(PASSWORD_PROPERTY));

        this.branchMappings = parseBranchMapping(TyrProperties.getProperty(BUILD_CONFIG));
    }

    @Override
    public void triggerBuild(JsonNode prPayload) {
        String branch = prPayload.get(Utils.BASE).get(Utils.REF).asText();
        if (!branchMappings.containsKey(branch)) {
            return;
        }

        String pull = prPayload.get(Utils.NUMBER).asText();
        String sha = prPayload.get(Utils.HEAD).get(Utils.SHA).asText();
        String buildId = branchMappings.get(branch);

        Client client = ClientBuilder.newClient();
        URI statusUri = UriBuilder
                .fromUri(baseUrl)
                .path("/app/rest/buildQueue")
                .build();

        WebTarget target = client.target(statusUri);

        Entity<BuildJson> json = Entity.json(new BuildJson("pull/" + pull, buildId,
                sha, pull, branch));

        Response response = target.request()
                .header(HttpHeaders.ACCEPT_ENCODING, "UTF-8")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encryptedCredentials)
                .post(json);

        log.info("Teamcity status update: " + response.getStatus());
        response.close();
    }


    private String encryptCredentials(String username, String password) {
        String authStr = username + ":" + password;
        try {
            return DatatypeConverter.printBase64Binary(authStr.getBytes("utf-8"));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Cannot encrypt credentials", e);
        }
    }

    private String getBaseUrl(String host, int port) {
        return port == 443 ? "https://" + host + "/httpAuth" : "http://" + host + ":" + port + "/httpAuth";
    }

    private Map<String, String> parseBranchMapping(String mappings) {
        Map<String, String> branchMap = new HashMap<>();

        for (String mapping : mappings.split(",")) {
            String[] parts = mapping.split("=>");
            branchMap.put(parts[0].trim(), parts[1].trim());
        }

        return branchMap;
    }
}