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
package org.apache.myfaces.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.faces.context.ExternalContext;

import org.apache.myfaces.buildtools.maven2.plugin.builder.annotation.JSFWebConfigParam;
import org.apache.myfaces.config.element.ManagedBean;
import org.apache.myfaces.config.element.NavigationCase;
import org.apache.myfaces.config.element.NavigationRule;
import org.apache.myfaces.shared.util.ClassUtils;

public class FacesConfigValidator
{

    @JSFWebConfigParam(since="2.0", defaultValue="false", expectedValues="true, false")
    public static final String VALIDATE_CONTEXT_PARAM = "org.apache.myfaces.VALIDATE";
    
    private FacesConfigValidator(){
        // hidden 
    }

    public static List<String> validate(ExternalContext ctx, String ctxPath){
        
        RuntimeConfig runtimeConfig = RuntimeConfig.getCurrentInstance(ctx);
        
        Map<String, ManagedBean> managedBeansMap = runtimeConfig.getManagedBeans();
        
        Collection<? extends ManagedBean> managedBeans = null;
        if (managedBeansMap != null)
        {
            managedBeans = managedBeansMap.values();
        }
        
        Collection<? extends NavigationRule> navRules = runtimeConfig.getNavigationRules();
        
        return validate(managedBeans, navRules, ctxPath);
        
    }
    
    public static List<String> validate(Collection<? extends ManagedBean> managedBeans, 
                                        Collection<? extends NavigationRule> navRules, String ctxPath)
    {
        
        List<String> list = new ArrayList<String>();
        
        if (managedBeans != null)
        {
            validateManagedBeans(managedBeans, list);
        }
        
        if (navRules != null)
        {
            validateNavRules(navRules, list, ctxPath);
        }
        
        return list;
    }

    private static void validateNavRules(Collection<? extends NavigationRule> navRules, List<String> list, 
                                         String ctxPath)
    {
        for (NavigationRule navRule : navRules)
        {
            validateNavRule(navRule, list, ctxPath);
        }
    }
    
    private static void validateNavRule(NavigationRule navRule, List<String> list, String ctxPath){
        
        String fromId = navRule.getFromViewId();
        String filePath = ctxPath + fromId;
        
        if(fromId != null && ! "*".equals(fromId) && ! new File(filePath).exists())
        {
            list.add("File for navigation 'from id' does not exist " + filePath);
        }            
            
        for (NavigationCase caze : navRule.getNavigationCases())
        {
            String toViewPath = ctxPath + caze.getToViewId();
            
            if(!new File(toViewPath).exists())
            {
                list.add("File for navigation 'to id' does not exist " + toViewPath);
            }
        }
    }
    
    private static void validateManagedBeans(Collection<? extends ManagedBean> managedBeans, List<String> list)
    {
        for (ManagedBean managedBean : managedBeans)
        {
            validateManagedBean(managedBean, list);
        }
    }

    private static void validateManagedBean(ManagedBean managedBean, List<String> list)
    {
        String className = managedBean.getManagedBeanClassName();
        
        try
        {
            ClassUtils.classForName(className);
        }
        catch (ClassNotFoundException e)
        { 
            String msg = "Could not locate class " 
                + className + " for managed bean '" + managedBean.getManagedBeanName() + "'";
            
            list.add(msg);
        }
    }
}
