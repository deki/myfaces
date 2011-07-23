package org.apache.myfaces.shared.renderkit;

import java.io.StringWriter;

import javax.faces.application.Application;
import javax.faces.application.ProjectStage;
import javax.faces.application.Resource;
import javax.faces.application.ResourceHandler;
import javax.faces.component.UIInput;
import javax.faces.component.html.HtmlGraphicImage;

import org.apache.myfaces.shared.renderkit.html.HTML;
import org.apache.myfaces.test.base.AbstractJsfTestCase;
import org.apache.myfaces.test.mock.MockResponseWriter;
import org.easymock.classextension.EasyMock;

/**
 * 
 * @author markoc
 */
public class RendererUtilsTest extends AbstractJsfTestCase {

	private MockResponseWriter writer;

	/**
	 * ResourceHandler nice easy mock
	 */
	ResourceHandler resourceHandlerMock;

	/**
	 * {@link Application} nice easy mock
	 */
	Application applicationMock;

	/**
	 * A {@link Resource} nice easy mock
	 */
	private Resource resourceMock;

	String libraryName = "images";

	String resourceName = "picture.gif";

	String requestPath = "/somePrefix/faces/javax.faces.resource/picture.gif?ln=\"images\"";

	// a Component instance:
	HtmlGraphicImage graphicImage = new HtmlGraphicImage();

	public RendererUtilsTest(String name) {
		super(name);
	}

	protected void setUp() throws Exception {
		super.setUp();

		writer = new MockResponseWriter(new StringWriter(), null, null);
		facesContext.setResponseWriter(writer);

		applicationMock = EasyMock.createNiceMock(Application.class);
		facesContext.setApplication(applicationMock);

		resourceHandlerMock = EasyMock.createNiceMock(ResourceHandler.class);
		applicationMock.getResourceHandler();
		EasyMock.expectLastCall().andReturn(resourceHandlerMock);
		
		applicationMock.getProjectStage();
		EasyMock.expectLastCall().andReturn(ProjectStage.Development);

		resourceMock = EasyMock.createNiceMock(Resource.class);

		EasyMock.replay(applicationMock);

		graphicImage.getAttributes().put(JSFAttr.LIBRARY_ATTR, libraryName);
		graphicImage.getAttributes().put(JSFAttr.NAME_ATTR, resourceName);
		graphicImage.setId("graphicImageId");
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	/**
	 * 
	 */
	public void testGetIconSrc() {

		// Training a mock:
		resourceHandlerMock.createResource(resourceName, libraryName);
		EasyMock.expectLastCall().andReturn(resourceMock);
		resourceMock.getRequestPath();
		EasyMock.expectLastCall().andReturn(requestPath);
		EasyMock.replay(resourceHandlerMock);
		EasyMock.replay(resourceMock);

		// Tested method :
		String iconSrc = RendererUtils.getIconSrc(facesContext, graphicImage,
				HTML.IMG_ELEM);

		assertEquals(
				"If name or name/library present, source must be obtained from ResourceHandler",
				requestPath, iconSrc);

	}

	public void testGetIconSrcResourceNotFound() throws Exception {
		// Training a mock:
		EasyMock.reset(resourceHandlerMock);
		resourceHandlerMock.createResource(resourceName, libraryName);
		EasyMock.expectLastCall().andReturn(null);
		EasyMock.replay(resourceHandlerMock);

		// Tested method :
		String iconSrc = RendererUtils.getIconSrc(facesContext, graphicImage,
				HTML.IMG_ELEM);

		assertEquals("RES_NOT_FOUND", iconSrc);
		assertTrue("If resource is not found, a Message must be added",
				facesContext.getMessages(graphicImage.getClientId(facesContext)).hasNext());

	}

    public void testGetStringValue()
    {
        // Test for situation where submittedValue IS NOT String: 
        UIInput uiInput = new UIInput();
        Object submittedValue = new Object();
        uiInput.setSubmittedValue(submittedValue);
        
        String stringValue = RendererUtils.getStringValue(facesContext, uiInput);
        assertNotNull(stringValue);
        assertEquals("If submittedvalue is not String, toString() must be used", submittedValue.toString(), stringValue);
    }

    public void testGetConvertedUIOutputValue()
    {
        UIInput uiInput = new UIInput();
        StringBuilder submittedValue = new StringBuilder("submittedValue");
        uiInput.setSubmittedValue(submittedValue);
        
        
        Object convertedUIOutputValue = RendererUtils.getConvertedUIOutputValue(facesContext, uiInput, submittedValue);
        assertEquals("If submittedvalue is not String, toString() must be used", submittedValue.toString(), convertedUIOutputValue);
    }

}
