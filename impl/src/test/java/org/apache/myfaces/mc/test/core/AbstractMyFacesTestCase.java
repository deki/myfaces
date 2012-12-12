/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.myfaces.mc.test.core;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.el.ExpressionFactory;
import javax.faces.FacesException;
import javax.faces.FactoryFinder;
import javax.faces.application.Application;
import javax.faces.application.FacesMessage;
import javax.faces.application.ProjectStage;
import javax.faces.application.ViewHandler;
import javax.faces.component.UIViewRoot;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.context.FacesContextFactory;
import javax.faces.context.Flash;
import javax.faces.event.ExceptionQueuedEvent;
import javax.faces.event.ExceptionQueuedEventContext;
import javax.faces.event.PhaseId;
import javax.faces.event.PhaseListener;
import javax.faces.event.PreRenderViewEvent;
import javax.faces.lifecycle.Lifecycle;
import javax.faces.lifecycle.LifecycleFactory;
import javax.faces.view.ViewDeclarationLanguage;
import javax.faces.webapp.FacesServlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.http.HttpServletResponse;

import org.apache.myfaces.config.ConfigFilesXmlValidationUtils;
import org.apache.myfaces.config.DefaultFacesConfigurationProvider;
import org.apache.myfaces.config.RuntimeConfig;
import org.apache.myfaces.config.element.FacesConfig;
import org.apache.myfaces.config.impl.digester.elements.Factory;
import org.apache.myfaces.lifecycle.LifecycleImpl;
import org.apache.myfaces.lifecycle.ViewNotFoundException;
import org.apache.myfaces.mc.test.core.annotation.DeclareFacesConfig;
import org.apache.myfaces.mc.test.core.annotation.ManagedBeans;
import org.apache.myfaces.mc.test.core.annotation.PageBean;
import org.apache.myfaces.shared.config.MyfacesConfig;
import org.apache.myfaces.spi.FacesConfigurationProvider;
import org.apache.myfaces.spi.impl.DefaultFacesConfigurationProviderFactory;
import org.apache.myfaces.test.el.MockExpressionFactory;
import org.apache.myfaces.test.mock.MockPrintWriter;
import org.apache.myfaces.test.mock.MockServletConfig;
import org.apache.myfaces.test.mock.MockServletContext;
import org.apache.myfaces.util.DebugUtils;
import org.apache.myfaces.view.facelets.FaceletViewDeclarationLanguage;
import org.apache.myfaces.webapp.AbstractFacesInitializer;
import org.apache.myfaces.webapp.StartupServletContextListener;
import org.junit.After;
import org.junit.Before;
import org.xml.sax.SAXException;

/**
 * <p>Abstract JUnit test case base class, which sets up MyFaces Core environment
 * using mock object for the outer servlet environment.</p>
 * <p>Since jsp engine is not bundled with MyFaces, this configuration is able to 
 * handle facelet pages only.</p>
 * 
 * @author Leonardo Uribe
 *
 */
public abstract class AbstractMyFacesTestCase
{
    private static Class<?> PHASE_EXECUTOR_CLASS = null;
    private static Class<?> PHASE_MANAGER_CLASS = null;
    
    static {
        try
        {
            PHASE_EXECUTOR_CLASS = Class.forName("org.apache.myfaces.lifecycle.PhaseExecutor");
            PHASE_MANAGER_CLASS = Class.forName("org.apache.myfaces.lifecycle.PhaseListenerManager");
        }
        catch (ClassNotFoundException e)
        {
            //No op
        }
    }
    
    public static final String PHASE_MANAGER_INSTANCE = "org.apache.myfaces.test.PHASE_MANAGER_INSTANCE";
    
    public static final String LAST_PHASE_PROCESSED = "oam.LAST_PHASE_PROCESSED";
    
    public static final String LAST_RENDER_PHASE_STEP = "oam.LAST_RENDER_PHASE_STEP";
    
    public static final int BEFORE_RENDER_STEP = 1;
    public static final int BUILD_VIEW_CYCLE_STEP = 2;
    public static final int VIEWHANDLER_RENDER_STEP = 3;
    public static final int AFTER_RENDER_STEP = 4;
    
    // ------------------------------------------------------------ Constructors

    /**
     * <p>Construct a new instance of this test case.</p>
     *
     * @param name Name of this test case
     */    
    public AbstractMyFacesTestCase()
    {
    }

    // ---------------------------------------------------- Overall Test Methods

    /**
     * <p>Set up instance variables required by this test case.</p>
     */
    @Before
    public void setUp() throws Exception
    {
        // Set up a new thread context class loader
        threadContextClassLoader = Thread.currentThread()
                .getContextClassLoader();
        Thread.currentThread()
                .setContextClassLoader(
                        new URLClassLoader(new URL[0], this.getClass()
                                .getClassLoader()));

        // Set up Servlet API Objects
        setUpServletObjects();

        // Set up JSF API Objects
        FactoryFinder.releaseFactories();

        setUpServletListeners();
        
        setUpFacesServlet();
    }
    
    /**
     * <p>Setup servlet objects that will be used for the test:</p>
     * 
     * <ul>
     * <li><code>servletConfig</code> (<code>MockServletConfig</code>)</li>
     * <li><code>servletContext</code> (<code>MockServletContext</code>)</li>
     * </ul>
     * 
     * @throws Exception
     */
    protected void setUpServletObjects() throws Exception
    {
        servletContext = new MockServletContext();
        servletConfig = new MockServletConfig(servletContext);
        servletContext.setDocumentRoot(getWebappContextURI());
        setUpWebConfigParams();
    }
    
    /**
     * <p>Setup web config params. By default it sets the following params</p>
     * 
     * <ul>
     * <li>"org.apache.myfaces.INITIALIZE_ALWAYS_STANDALONE", "true"</li>
     * <li>"javax.faces.PROJECT_STAGE", "UnitTest"</li>
     * <li>"javax.faces.PARTIAL_STATE_SAVING", "true"</li>
     * <li>"javax.faces.FACELETS_REFRESH_PERIOD", "-1"</li>
     * </ul>
     * 
     * @throws Exception
     */
    protected void setUpWebConfigParams() throws Exception
    {
        servletContext.addInitParameter("org.apache.myfaces.INITIALIZE_ALWAYS_STANDALONE", "true");
        servletContext.addInitParameter("javax.faces.PROJECT_STAGE", "UnitTest");
        servletContext.addInitParameter("javax.faces.PARTIAL_STATE_SAVING", "true");
        servletContext.addInitParameter(FaceletViewDeclarationLanguage.PARAM_REFRESH_PERIOD,"-1");
    }
    
    /**
     * <p>Return an URI that identifies the base path that will be used by servletContext
     * to load resources like facelet files an others. By default it points to the directory
     * path calculated from the package name of the child test class.</p>
     * 
     * @return
     */
    protected URI getWebappContextURI()
    {
        try
        {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            URL url = cl.getResource(getWebappContextFilePath());
            if (url == null)
            {
                throw new FileNotFoundException(cl.getResource("").getFile()
                        + getWebappContextFilePath() + " was not found");
            }
            else
            {
                return new URI(url.toString());
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error Initializing Context", e);
        }
    }
    
    /**
     * Return a path that is used to load resources like facelet files an others.
     * By default it points to the directory path calculated from the package 
     * name of the child test class.
     * 
     * @return
     */
    protected String getWebappContextFilePath()
    {
        return this.getClass().getName().substring(0,
                this.getClass().getName().lastIndexOf('.')).replace('.', '/')
                + "/";
    }
    
    /**
     * Create the ExpressionFactory instance that will be used to initialize the test
     * environment. By default it uses MockExpressionFactory. 
     * 
     * @return
     */
    protected ExpressionFactory createExpressionFactory()
    {
        return new MockExpressionFactory();
    }
    
    /**
     * setup servlets avaliable in the test environment
     * 
     * @throws Exception
     */
    protected void setUpServlets() throws Exception
    {
        setUpFacesServlet();
    }
    
    /**
     * setup listeners avaliable in the test environment
     * 
     * @throws Exception
     */
    protected void setUpServletListeners() throws Exception
    {
        setUpMyFaces();
    }
    
    /**
     * 
     * @return
     */
    protected FacesConfigurationProvider createFacesConfigurationProvider()
    {
        return new MyFacesMockFacesConfigurationProvider(this); 
    }
    
    protected void setUpMyFaces() throws Exception
    {
        if (facesConfigurationProvider == null)
        {
            facesConfigurationProvider = createFacesConfigurationProvider();
        }
        servletContext.setAttribute(
                DefaultFacesConfigurationProviderFactory.FACES_CONFIGURATION_PROVIDER_INSTANCE_KEY, 
                facesConfigurationProvider);
        listener = new StartupServletContextListener();
        listener.setFacesInitializer(new AbstractFacesInitializer()
        {
            
            @Override
            protected void initContainerIntegration(ServletContext servletContext,
                    ExternalContext externalContext)
            {
                ExpressionFactory expressionFactory = createExpressionFactory();

                RuntimeConfig runtimeConfig = buildConfiguration(servletContext, externalContext, expressionFactory);
            }
        });
        listener.contextInitialized(new ServletContextEvent(servletContext));
    }

    protected void tearDownMyFaces() throws Exception
    {
        //Don't tear down FacesConfigurationProvider, because that is shared by all tests.
        //This helps to reduce the time each test takes 
        //facesConfigurationProvider = null
        
        listener.contextDestroyed(new ServletContextEvent(servletContext));
    }

    protected void setUpFacesServlet() throws Exception
    {
        lifecycleFactory = (LifecycleFactory)FactoryFinder.getFactory(FactoryFinder.LIFECYCLE_FACTORY);
        facesContextFactory = (FacesContextFactory)FactoryFinder.getFactory(FactoryFinder.FACES_CONTEXT_FACTORY);
        lifecycle = lifecycleFactory.getLifecycle(getLifecycleId());
    }
    
    protected void tearDownFacesServlet() throws Exception
    {
        lifecycleFactory = null;
        facesContextFactory = null;
    }
    
    protected void tearDownServlets() throws Exception
    {
        tearDownFacesServlet();
    }
    
    protected void tearDownServletListeners() throws Exception
    {
        tearDownMyFaces();
    }

    @After
    public void tearDown() throws Exception
    {
        tearDownServlets();

        tearDownServletListeners();
        
        listener = null;
        
        servletConfig = null;
        servletContext = null;
        
        FactoryFinder.releaseFactories();
        
        Thread.currentThread().setContextClassLoader(threadContextClassLoader);
        threadContextClassLoader = null;
    }
    
    private String getLifecycleId()
    {
        // 1. check for Servlet's init-param
        // 2. check for global context parameter
        // 3. use default Lifecycle Id, if none of them was provided
        String serLifecycleId = servletConfig.getInitParameter(FacesServlet.LIFECYCLE_ID_ATTR);
        String appLifecycleId = servletConfig.getServletContext().getInitParameter(FacesServlet.LIFECYCLE_ID_ATTR);
        appLifecycleId = serLifecycleId == null ? appLifecycleId : serLifecycleId;
        return appLifecycleId != null ? appLifecycleId : LifecycleFactory.DEFAULT_LIFECYCLE;
    }

    /**
     * Call lifecycle.execute(facesContext)
     * 
     * @param facesContext
     */
    protected void processLifecycleExecute(FacesContext facesContext)
    {
        lifecycle.execute(facesContext);
        facesContext.getAttributes().put(LAST_PHASE_PROCESSED, PhaseId.INVOKE_APPLICATION);
    }

    /**
     * Execute restore view phase.
     * 
     * @param facesContext
     * @throws Exception
     */
    protected void processRestoreViewPhase(FacesContext facesContext) throws Exception
    {
        executePhase(facesContext, PhaseId.RESTORE_VIEW);
        facesContext.getAttributes().put(LAST_PHASE_PROCESSED, PhaseId.RESTORE_VIEW);
    }
    
    /**
     * Execute apply request values phase. If the responseComplete or renderResponse
     * flags are set, it returns without do any action.
     * 
     * @param facesContext
     * @throws Exception
     */
    protected void processApplyRequestValuesPhase(FacesContext facesContext) throws Exception
    {
        if (facesContext.getRenderResponse() || facesContext.getResponseComplete())
        {
            return;
        }
        executePhase(facesContext, PhaseId.APPLY_REQUEST_VALUES);
        facesContext.getAttributes().put(LAST_PHASE_PROCESSED, PhaseId.APPLY_REQUEST_VALUES);
    }

    /**
     * Execute process validations phase. If the responseComplete or renderResponse
     * flags are set, it returns without do any action.
     * 
     * @param facesContext
     * @throws Exception
     */
    protected void processValidationsPhase(FacesContext facesContext) throws Exception
    {
        if (facesContext.getRenderResponse() || facesContext.getResponseComplete())
        {
            return;
        }
        executePhase(facesContext, PhaseId.PROCESS_VALIDATIONS);
        facesContext.getAttributes().put(LAST_PHASE_PROCESSED, PhaseId.PROCESS_VALIDATIONS);
    }

    /**
     * Execute update model phase. If the responseComplete or renderResponse
     * flags are set, it returns without do any action.
     * 
     * @param facesContext
     * @throws Exception
     */
    protected void processUpdateModelPhase(FacesContext facesContext) throws Exception
    {
        if (facesContext.getRenderResponse() || facesContext.getResponseComplete())
        {
            return;
        }
        executePhase(facesContext, PhaseId.UPDATE_MODEL_VALUES);
        facesContext.getAttributes().put(LAST_PHASE_PROCESSED, PhaseId.UPDATE_MODEL_VALUES);

    }
    
    /**
     * Execute invoke application phase. If the responseComplete or renderResponse
     * flags are set, it returns without do any action.
     * 
     * @param facesContext
     * @throws Exception
     */
    protected void processInvokeApplicationPhase(FacesContext facesContext) throws Exception
    {
        if (facesContext.getRenderResponse() || facesContext.getResponseComplete())
        {
            return;
        }
        executePhase(facesContext, PhaseId.INVOKE_APPLICATION);
        facesContext.getAttributes().put(LAST_PHASE_PROCESSED, PhaseId.INVOKE_APPLICATION);
    }

    /**
     * Call lifecycle.render(facesContext)
     * 
     * @param facesContext
     */
    protected void processRender(FacesContext facesContext) throws Exception
    {
        processRemainingExecutePhases(facesContext);
        lifecycle.render(facesContext);
        facesContext.getAttributes().put(LAST_PHASE_PROCESSED, PhaseId.RENDER_RESPONSE);
        facesContext.getAttributes().put(LAST_RENDER_PHASE_STEP, AFTER_RENDER_STEP);
    }
    
    protected void processRemainingExecutePhases(FacesContext facesContext) throws Exception
    {
        PhaseId lastPhaseId = (PhaseId) facesContext.getAttributes().get(LAST_PHASE_PROCESSED);
        if (lastPhaseId == null)
        {
            processLifecycleExecute(facesContext);
            return;
        }
        else
        {
            boolean continueProcess = false;
            if (PhaseId.RESTORE_VIEW.equals(lastPhaseId))
            {
                processApplyRequestValuesPhase(facesContext);
                continueProcess = true;
            }
            if (continueProcess || PhaseId.APPLY_REQUEST_VALUES.equals(lastPhaseId))
            {
                processValidationsPhase(facesContext);
                continueProcess = true;
            }
            if (continueProcess || PhaseId.PROCESS_VALIDATIONS.equals(lastPhaseId))
            {
                processUpdateModelPhase(facesContext);
                continueProcess = true;
            }
            if (continueProcess || PhaseId.UPDATE_MODEL_VALUES.equals(lastPhaseId))
            {
                processInvokeApplicationPhase(facesContext);
                continueProcess = true;
            }
        }
    }

    protected void processRemainingPhases(FacesContext facesContext) throws Exception
    {
        PhaseId lastPhaseId = (PhaseId) facesContext.getAttributes().get(LAST_PHASE_PROCESSED);
        if (lastPhaseId == null)
        {
            processLifecycleExecute(facesContext);
            processRender(facesContext);
            return;
        }
        else
        {
            boolean continueProcess = false;
            if (PhaseId.RESTORE_VIEW.equals(lastPhaseId))
            {
                processApplyRequestValuesPhase(facesContext);
                continueProcess = true;
            }
            if (continueProcess || PhaseId.APPLY_REQUEST_VALUES.equals(lastPhaseId))
            {
                processValidationsPhase(facesContext);
                continueProcess = true;
            }
            if (continueProcess || PhaseId.PROCESS_VALIDATIONS.equals(lastPhaseId))
            {
                processUpdateModelPhase(facesContext);
                continueProcess = true;
            }
            if (continueProcess || PhaseId.UPDATE_MODEL_VALUES.equals(lastPhaseId))
            {
                processInvokeApplicationPhase(facesContext);
                continueProcess = true;
            }
            if (continueProcess || PhaseId.INVOKE_APPLICATION.equals(lastPhaseId))
            {
                Integer step = (Integer) facesContext.getAttributes().get(LAST_RENDER_PHASE_STEP);
                if (step == null)
                {
                    processRender(facesContext);
                }
                else
                {
                    if (BEFORE_RENDER_STEP == step.intValue())
                    {
                        executeBuildViewCycle(facesContext);
                        executeViewHandlerRender(facesContext);
                        executeAfterRender(facesContext);
                    }
                    else if (BUILD_VIEW_CYCLE_STEP == step.intValue())
                    {
                        executeViewHandlerRender(facesContext);
                        executeAfterRender(facesContext);
                    }
                    else if (VIEWHANDLER_RENDER_STEP == step.intValue())
                    {
                        executeAfterRender(facesContext);
                    }
                }
            }
        }
    }
    
    /**
     * Indicate if annotation scanning should be done over the classpath. 
     * By default it is set to false.
     * 
     * @return
     */
    protected boolean isScanAnnotations()
    {
        return false;
    }
    
    protected void executeBeforeRender(FacesContext facesContext) throws Exception
    {
        if (lifecycle instanceof LifecycleImpl)
        {
            LifecycleImpl lifecycleImpl = (LifecycleImpl) lifecycle;
            
            Object phaseExecutor = null;
            Field renderExecutorField = lifecycleImpl.getClass().getDeclaredField("renderExecutor");
            if (!renderExecutorField.isAccessible())
            {
                renderExecutorField.setAccessible(true);
            }
            phaseExecutor = renderExecutorField.get(lifecycleImpl);
            
            if (facesContext.getResponseComplete())
            {
                return;
            }
            
            Object phaseManager = facesContext.getAttributes().get(PHASE_MANAGER_INSTANCE);
            if (phaseManager == null)
            {
                Method getPhaseListenersMethod = lifecycleImpl.getClass().getDeclaredMethod("getPhaseListeners");
                if (!getPhaseListenersMethod.isAccessible())
                {
                    getPhaseListenersMethod.setAccessible(true);
                }
                
                Constructor<?> plmc = PHASE_MANAGER_CLASS.getDeclaredConstructor(new Class[]{Lifecycle.class, FacesContext.class, PhaseListener[].class});
                if (!plmc.isAccessible())
                {
                    plmc.setAccessible(true);
                }
                phaseManager = plmc.newInstance(lifecycle, facesContext, getPhaseListenersMethod.invoke(lifecycleImpl, null));
                facesContext.getAttributes().put(PHASE_MANAGER_INSTANCE, phaseManager);
            }
            
            Flash flash = facesContext.getExternalContext().getFlash();
            
            try
            {
                facesContext.setCurrentPhaseId(PhaseId.RENDER_RESPONSE);
                
                flash.doPrePhaseActions(facesContext);
                
                // let the PhaseExecutor do some pre-phase actions
                
                //renderExecutor.doPrePhaseActions(facesContext);
                Method doPrePhaseActionsMethod = phaseExecutor.getClass().getMethod("doPrePhaseActions", FacesContext.class);
                if(!(doPrePhaseActionsMethod.isAccessible()))
                {
                    doPrePhaseActionsMethod.setAccessible(true);
                }
                doPrePhaseActionsMethod.invoke(phaseExecutor, facesContext);
                
                //phaseListenerMgr.informPhaseListenersBefore(PhaseId.RENDER_RESPONSE);
                Method informPhaseListenersBeforeMethod = phaseManager.getClass().getDeclaredMethod("informPhaseListenersBefore", PhaseId.class);
                if(!(informPhaseListenersBeforeMethod.isAccessible()))
                {
                    informPhaseListenersBeforeMethod.setAccessible(true);
                }
                informPhaseListenersBeforeMethod.invoke(phaseManager, PhaseId.RENDER_RESPONSE);
                
                // also possible that one of the listeners completed the response
                if (facesContext.getResponseComplete())
                {
                    return;
                }
                
                //renderExecutor.execute(facesContext);
            }
            
            catch (Throwable e)
            {
                // JSF 2.0: publish the executor's exception (if any).
                ExceptionQueuedEventContext context = new ExceptionQueuedEventContext (facesContext, e, null, PhaseId.RENDER_RESPONSE);
                facesContext.getApplication().publishEvent (facesContext, ExceptionQueuedEvent.class, context);
            }
            
            finally
            {
                /*
                phaseListenerMgr.informPhaseListenersAfter(renderExecutor.getPhase());
                flash.doPostPhaseActions(facesContext);
                
                // publish a field in the application map to indicate
                // that the first request has been processed
                requestProcessed(facesContext);
                */
            }
            
            facesContext.getExceptionHandler().handle();
            

            facesContext.getAttributes().remove(PHASE_MANAGER_INSTANCE);
            
            facesContext.getAttributes().put(LAST_RENDER_PHASE_STEP, BEFORE_RENDER_STEP);
        }
        else
        {
            throw new UnsupportedOperationException("Cannot execute phase on custom lifecycle instances");
        }
    }
    
    public void executeBuildViewCycle(FacesContext facesContext) throws Exception
    {
        Application application = facesContext.getApplication();
        ViewHandler viewHandler = application.getViewHandler();
        UIViewRoot root;
        UIViewRoot previousRoot;
        String viewId;
        String newViewId;
        boolean isNotSameRoot;
        int loops = 0;
        int maxLoops = 15;
        
        if (facesContext.getViewRoot() == null)
        {
            throw new ViewNotFoundException("A view is required to execute "+facesContext.getCurrentPhaseId());
        }
        
        try
        {
            // do-while, because the view might change in PreRenderViewEvent-listeners
            do
            {
                root = facesContext.getViewRoot();
                previousRoot = root;
                viewId = root.getViewId();
                
                ViewDeclarationLanguage vdl = viewHandler.getViewDeclarationLanguage(
                        facesContext, viewId);
                if (vdl != null)
                {
                    vdl.buildView(facesContext, root);
                }
                
                // publish a PreRenderViewEvent: note that the event listeners
                // of this event can change the view, so we have to perform the algorithm 
                // until the viewId does not change when publishing this event.
                application.publishEvent(facesContext, PreRenderViewEvent.class, root);
                
                // was the response marked as complete by an event listener?
                if (facesContext.getResponseComplete())
                {
                    return;
                }

                root = facesContext.getViewRoot();
                
                newViewId = root.getViewId();
                
                isNotSameRoot = !( (newViewId == null ? newViewId == viewId : newViewId.equals(viewId) ) && 
                        previousRoot.equals(root) ); 
                
                loops++;
            }
            while ((newViewId == null && viewId != null) 
                    || (newViewId != null && (!newViewId.equals(viewId) || isNotSameRoot ) ) && loops < maxLoops);
            
            if (loops == maxLoops)
            {
                // PreRenderView reach maxLoops - probably a infinitive recursion:
                boolean production = facesContext.isProjectStage(ProjectStage.Production);
                /*
                Level level = production ? Level.FINE : Level.WARNING;
                if (log.isLoggable(level))
                {
                    log.log(level, "Cicle over buildView-PreRenderViewEvent on RENDER_RESPONSE phase "
                                   + "reaches maximal limit, please check listeners for infinite recursion.");
                }*/
            }
            
            facesContext.getAttributes().put(LAST_RENDER_PHASE_STEP, BUILD_VIEW_CYCLE_STEP);
        }
        catch (IOException e)
        {
            throw new FacesException(e.getMessage(), e);
        }
    }
    
    public void executeViewHandlerRender(FacesContext facesContext)
    {
        Application application = facesContext.getApplication();
        ViewHandler viewHandler = application.getViewHandler();

        try
        {
            viewHandler.renderView(facesContext, facesContext.getViewRoot());
            
            // log all unhandled FacesMessages, don't swallow them
            // perf: org.apache.myfaces.context.servlet.FacesContextImpl.getMessageList() creates
            // new Collections.unmodifiableList with every invocation->  call it only once
            // and messageList is RandomAccess -> use index based loop
            List<FacesMessage> messageList = facesContext.getMessageList();
            if (!messageList.isEmpty())
            {
                StringBuilder builder = new StringBuilder();
                //boolean shouldLog = false;
                for (int i = 0, size = messageList.size(); i < size; i++)
                {
                    FacesMessage message = messageList.get(i);
                    if (!message.isRendered())
                    {
                        builder.append("\n- ");
                        builder.append(message.getDetail());
                        
                        //shouldLog = true;
                    }
                }
                /*
                if (shouldLog)
                {
                    log.log(Level.WARNING, "There are some unhandled FacesMessages, " +
                            "this means not every FacesMessage had a chance to be rendered.\n" +
                            "These unhandled FacesMessages are: " + builder.toString());
                }*/
            }
            facesContext.getAttributes().put(LAST_RENDER_PHASE_STEP, VIEWHANDLER_RENDER_STEP);
        }
        catch (IOException e)
        {
            throw new FacesException(e.getMessage(), e);
        }
    }
    
    public void executeAfterRender(FacesContext facesContext) throws Exception
    {
        if (lifecycle instanceof LifecycleImpl)
        {
            LifecycleImpl lifecycleImpl = (LifecycleImpl) lifecycle;
            
            Object phaseExecutor = null;
            Field renderExecutorField = lifecycleImpl.getClass().getDeclaredField("renderExecutor");
            if (!renderExecutorField.isAccessible())
            {
                renderExecutorField.setAccessible(true);
            }
            phaseExecutor = renderExecutorField.get(lifecycleImpl);
            
            Object phaseManager = facesContext.getAttributes().get(PHASE_MANAGER_INSTANCE);
            if (phaseManager == null)
            {
                Method getPhaseListenersMethod = lifecycleImpl.getClass().getDeclaredMethod("getPhaseListeners");
                if (!getPhaseListenersMethod.isAccessible())
                {
                    getPhaseListenersMethod.setAccessible(true);
                }
                
                Constructor<?> plmc = PHASE_MANAGER_CLASS.getDeclaredConstructor(new Class[]{Lifecycle.class, FacesContext.class, PhaseListener[].class});
                if (!plmc.isAccessible())
                {
                    plmc.setAccessible(true);
                }
                phaseManager = plmc.newInstance(lifecycle, facesContext, getPhaseListenersMethod.invoke(lifecycleImpl, null));
                facesContext.getAttributes().put(PHASE_MANAGER_INSTANCE, phaseManager);
            }
            
            
            Flash flash = facesContext.getExternalContext().getFlash();
            
            //phaseListenerMgr.informPhaseListenersAfter(renderExecutor.getPhase());
            Method informPhaseListenersAfterMethod = phaseManager.getClass().getDeclaredMethod("informPhaseListenersAfter", PhaseId.class);
            if(!(informPhaseListenersAfterMethod.isAccessible()))
            {
                informPhaseListenersAfterMethod.setAccessible(true);
            }
            informPhaseListenersAfterMethod.invoke(phaseManager, PhaseId.RENDER_RESPONSE);
            
            flash.doPostPhaseActions(facesContext);
            
            facesContext.getExceptionHandler().handle();

            facesContext.getAttributes().remove(PHASE_MANAGER_INSTANCE);
            
            facesContext.getAttributes().put(LAST_RENDER_PHASE_STEP, AFTER_RENDER_STEP);
            //End render response phase
            facesContext.getAttributes().put(LAST_PHASE_PROCESSED, PhaseId.RENDER_RESPONSE);
        }
        else
        {
            throw new UnsupportedOperationException("Cannot execute phase on custom lifecycle instances");
        }
    }
    
    /**
     * Execute an specified phase, doing some reflection over LifecycleImpl.
     * 
     * @param facesContext
     * @param phase
     * @throws Exception
     */
    protected void executePhase(FacesContext facesContext, PhaseId phase) throws Exception
    {
        if (lifecycle instanceof LifecycleImpl)
        {
            LifecycleImpl lifecycleImpl = (LifecycleImpl) lifecycle;
            
            int phaseId = phase.equals(PhaseId.RESTORE_VIEW) ? 0 :
                          phase.equals(PhaseId.APPLY_REQUEST_VALUES) ? 1 : 
                          phase.equals(PhaseId.PROCESS_VALIDATIONS) ? 2 :
                          phase.equals(PhaseId.UPDATE_MODEL_VALUES) ? 3 : 
                          phase.equals(PhaseId.INVOKE_APPLICATION) ? 4 : 5 ;
            
            Object phaseExecutor = null;
            if (phaseId < 5)
            {
                Field lifecycleExecutorsField = lifecycleImpl.getClass().getDeclaredField("lifecycleExecutors");
                if (!lifecycleExecutorsField.isAccessible())
                {
                    lifecycleExecutorsField.setAccessible(true);
                }
                phaseExecutor = ((Object[])lifecycleExecutorsField.get(lifecycleImpl))[phaseId];
            }
            else
            {
                Field renderExecutorField = lifecycleImpl.getClass().getDeclaredField("renderExecutor");
                if (!renderExecutorField.isAccessible())
                {
                    renderExecutorField.setAccessible(true);
                }
                phaseExecutor = renderExecutorField.get(lifecycleImpl);
            }
            
            Object phaseManager = facesContext.getAttributes().get(PHASE_MANAGER_INSTANCE);
            if (phaseManager == null)
            {
                Method getPhaseListenersMethod = lifecycleImpl.getClass().getDeclaredMethod("getPhaseListeners");
                if (!getPhaseListenersMethod.isAccessible())
                {
                    getPhaseListenersMethod.setAccessible(true);
                }
                
                Constructor<?> plmc = PHASE_MANAGER_CLASS.getDeclaredConstructor(new Class[]{Lifecycle.class, FacesContext.class, PhaseListener[].class});
                if (!plmc.isAccessible())
                {
                    plmc.setAccessible(true);
                }
                phaseManager = plmc.newInstance(lifecycle, facesContext, getPhaseListenersMethod.invoke(lifecycleImpl, null));
                facesContext.getAttributes().put(PHASE_MANAGER_INSTANCE, phaseManager);
            }
            
            Method executePhaseMethod = lifecycleImpl.getClass().getDeclaredMethod("executePhase", new Class[]{
                    FacesContext.class, PHASE_EXECUTOR_CLASS, PHASE_MANAGER_CLASS});
            if (!executePhaseMethod.isAccessible())
            {
                executePhaseMethod.setAccessible(true);
            }
            
            executePhaseMethod.invoke(lifecycleImpl, facesContext, phaseExecutor, phaseManager);
            
            if (phase.equals(PhaseId.RENDER_RESPONSE))
            {
                facesContext.getAttributes().remove(PHASE_MANAGER_INSTANCE);
            }
        }
        else
        {
            throw new UnsupportedOperationException("Cannot execute phase on custom lifecycle instances");
        }
    }
    
    protected String getRenderedContent(FacesContext facesContext) throws IOException
    {
        MockPrintWriter writer1 = (MockPrintWriter) (((HttpServletResponse) facesContext.getExternalContext().getResponse()).getWriter());
        return String.valueOf(writer1.content());
    }

    // ------------------------------------------------------ Instance Variables


    // Thread context class loader saved and restored after each test
    private ClassLoader threadContextClassLoader = null;

    // Servlet objects 
    protected MockServletConfig servletConfig = null;
    protected MockServletContext servletContext = null;

    // MyFaces specific objects created by the servlet environment
    protected StartupServletContextListener listener = null;
    protected FacesConfigurationProvider facesConfigurationProvider = null;
    
    protected FacesContextFactory facesContextFactory = null;
    protected LifecycleFactory lifecycleFactory = null;
    protected Lifecycle lifecycle;

    private static FacesConfig standardFacesConfig;

    // ------------------------------------------------------ Subclasses

    /**
     * Mock FacesConfigurationProvider that replace the original ViewDeclarationLanguageFactory with a customized one that
     * contains only facelets vdl and cache some FacesConfig that does not change to reduce the time required to process each test.
     * 
     * @author Leonardo Uribe
     *
     */
    protected class MyFacesMockFacesConfigurationProvider extends DefaultFacesConfigurationProvider
    {
        private AbstractMyFacesTestCase testCase;
        
        public MyFacesMockFacesConfigurationProvider(AbstractMyFacesTestCase testCase)
        {
            this.testCase = testCase;
        }
        
        @Override
        public FacesConfig getStandardFacesConfig(ExternalContext ectx)
        {
            if (standardFacesConfig == null)
            {
                FacesConfig sfc = super.getStandardFacesConfig(ectx);
                Factory factory = (Factory) sfc.getFactories().get(0);
                // Override the default vdl factory with a mock one that only load
                // facelet views
                factory.getViewDeclarationLanguageFactory().set(0, MockMyFacesViewDeclarationLanguageFactory.class.getName());
                standardFacesConfig = sfc;
            }
            return standardFacesConfig;
        }

        @Override
        public FacesConfig getAnnotationsFacesConfig(ExternalContext ectx,
                boolean metadataComplete)
        {
            FacesConfig facesConfig = null;
            if (isScanAnnotations())
            {
                facesConfig = super.getAnnotationsFacesConfig(ectx, metadataComplete); 
            }
            
            ManagedBeans annoManagedBeans = testCase.getClass().getAnnotation(ManagedBeans.class);
            if (annoManagedBeans != null)
            {
                if (facesConfig == null)
                {
                    facesConfig = new org.apache.myfaces.config.impl.digester.elements.FacesConfig();
                }
                for (PageBean annoPageBean : annoManagedBeans.values())
                {
                    org.apache.myfaces.config.impl.digester.elements.ManagedBean bean = new 
                        org.apache.myfaces.config.impl.digester.elements.ManagedBean();
                    bean.setBeanClass(annoPageBean.clazz().getName());
                    bean.setName(annoPageBean.name() == null ? annoPageBean.clazz().getName() : annoPageBean.name());
                    bean.setScope(annoPageBean.scope() == null ? "request" : annoPageBean.scope());
                    bean.setEager(Boolean.toString(annoPageBean.eager()));
                    
                    ((org.apache.myfaces.config.impl.digester.elements.FacesConfig)facesConfig).addManagedBean(bean);
                }
            }

            PageBean annoPageBean = testCase.getClass().getAnnotation(PageBean.class);
            if (annoPageBean != null)
            {
                if (facesConfig == null)
                {
                    facesConfig = new org.apache.myfaces.config.impl.digester.elements.FacesConfig();
                }
                org.apache.myfaces.config.impl.digester.elements.ManagedBean bean = new 
                    org.apache.myfaces.config.impl.digester.elements.ManagedBean();
                bean.setBeanClass(annoPageBean.clazz().getName());
                bean.setName(annoPageBean.name() == null ? annoPageBean.clazz().getName() : annoPageBean.name());
                bean.setScope(annoPageBean.scope() == null ? "request" : annoPageBean.scope());
                bean.setEager(Boolean.toString(annoPageBean.eager()));
                
                ((org.apache.myfaces.config.impl.digester.elements.FacesConfig)facesConfig).addManagedBean(bean);
            }
            return facesConfig;
        }
        
        @Override
        public List<FacesConfig> getContextSpecifiedFacesConfig(ExternalContext ectx)
        {
            List<FacesConfig> appConfigResources = super.getContextSpecifiedFacesConfig(ectx);
            
            DeclareFacesConfig annoFacesConfig = testCase.getClass().getAnnotation(DeclareFacesConfig.class);
            if (annoFacesConfig != null)
            {
                Logger log = Logger.getLogger(testCase.getClass().getName());
                try
                {
                    for (String systemId : annoFacesConfig.value())
                    {
                        if (MyfacesConfig.getCurrentInstance(ectx).isValidateXML())
                        {
                            URL url = ectx.getResource(systemId);
                            if (url != null)
                            {
                                validateFacesConfig(ectx, url);
                            }
                        }   
                        InputStream stream = ectx.getResourceAsStream(systemId);
                        if (stream == null)
                        {
                            
                            log.severe("Faces config resource " + systemId + " not found");
                            continue;
                        }
            
                        if (log.isLoggable(Level.INFO))
                        {
                            log.info("Reading config " + systemId);
                        }
                        appConfigResources.add(getUnmarshaller(ectx).getFacesConfig(stream, systemId));
                        //getDispenser().feed(getUnmarshaller().getFacesConfig(stream, systemId));
                        stream.close();
    
                    }
                }
                catch (Throwable e)
                {
                    throw new FacesException(e);
                }
            }
            return appConfigResources;
        }
    }
    
    private void validateFacesConfig(ExternalContext ectx, URL url) throws IOException, SAXException
    {
        String version = ConfigFilesXmlValidationUtils.getFacesConfigVersion(url);
        if ("1.2".equals(version) || "2.0".equals(version) || "2.1".equals(version))
        {
            ConfigFilesXmlValidationUtils.validateFacesConfigFile(url, ectx, version);
        }
    }
}
