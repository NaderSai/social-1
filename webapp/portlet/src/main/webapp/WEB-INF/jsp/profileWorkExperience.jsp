<%@page import="org.exoplatform.social.rest.entity.ProfileEntity"%>
<%@page import="org.exoplatform.social.rest.api.EntityBuilder"%>
<%@ page import="org.exoplatform.social.core.manager.IdentityManager"%>
<%@ page import="org.exoplatform.social.core.identity.model.Identity"%>
<%@ page import="org.exoplatform.commons.utils.CommonsUtils"%>
<%@ page import="org.exoplatform.social.webui.Utils"%>
<%
  IdentityManager identityManager = CommonsUtils.getService(IdentityManager.class);
	String profileOwnerId = Utils.getOwnerIdentityId();
	Identity identity = identityManager.getIdentity(profileOwnerId);
	ProfileEntity profileEntity = EntityBuilder.buildEntityProfile(identity.getProfile(),
			"/rest/v1/social/users/" + profileOwnerId, "all");
	String jsonProfileEntity = EntityBuilder.toJsonString(profileEntity);
	int maxUploadSize = identityManager.getImageUploadLimit();
%>
<div class="VuetifyApp">
  <div data-app="true"
    class="v-application white v-application--is-ltr theme--light profileWorkExperience"
    id="ProfileWorkExperience">
    <v-cacheable-dom-app cache-id="ProfileWorkExperience_<%=profileOwnerId%>"></v-cacheable-dom-app>
    <textarea id="profileWorkExperienceDefaultValue" class="hidden"><%=jsonProfileEntity == null ? "{}" : jsonProfileEntity%></textarea>
    <script type="text/javascript">
      require(['PORTLET/social-portlet/ProfileWorkExperience'], 
          app => app.init(
              JSON.parse(document.getElementById('profileWorkExperienceDefaultValue').value), 
              <%=maxUploadSize%>)
      );
    </script>
  </div>
</div>