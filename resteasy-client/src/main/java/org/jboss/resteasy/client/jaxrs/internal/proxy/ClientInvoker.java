package org.jboss.resteasy.client.jaxrs.internal.proxy;

import org.jboss.resteasy.client.jaxrs.ProxyConfig;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocationBuilder;
import org.jboss.resteasy.client.jaxrs.internal.ClientRequestHeaders;
import org.jboss.resteasy.client.jaxrs.internal.ClientResponse;
import org.jboss.resteasy.client.jaxrs.internal.proxy.extractors.ClientContext;
import org.jboss.resteasy.client.jaxrs.internal.proxy.extractors.DefaultEntityExtractorFactory;
import org.jboss.resteasy.client.jaxrs.internal.proxy.extractors.EntityExtractor;
import org.jboss.resteasy.client.jaxrs.internal.proxy.processors.InvocationProcessor;
import org.jboss.resteasy.client.jaxrs.internal.proxy.processors.ProcessorFactory;
import org.jboss.resteasy.client.jaxrs.internal.proxy.processors.WebTargetProcessor;
import org.jboss.resteasy.util.FeatureContextDelegate;
import org.jboss.resteasy.util.MediaTypeHelper;

import javax.ws.rs.Path;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.MediaType;

import java.lang.reflect.Method;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class ClientInvoker implements MethodInvoker
{
   protected String httpMethod;
   protected Method method;
   protected Class<?> declaring;
   protected MediaType[] accepts;
   protected Object[] processors;
   protected ResteasyWebTarget webTarget;
   protected boolean followRedirects;
   @SuppressWarnings("rawtypes")
   protected EntityExtractor extractor;
   protected DefaultEntityExtractorFactory entityExtractorFactory;
   protected ClientConfiguration invokerConfig;


   public ClientInvoker(ResteasyWebTarget parent, Class<?> declaring, Method method, ProxyConfig config)
   {
      // webTarget must be a clone so that it has a cloned ClientConfiguration so we can apply DynamicFeature
      if (method.isAnnotationPresent(Path.class))
      {
         this.webTarget = parent.path(method);
      }
      else
      {
         this.webTarget = parent.clone();
      }
      this.declaring = declaring;
      this.method = method;
      invokerConfig = (ClientConfiguration) this.webTarget.getConfiguration();
      ResourceInfo info = new ResourceInfo()
      {
         @Override
         public Method getResourceMethod()
         {
            return ClientInvoker.this.method;
         }

         @Override
         public Class<?> getResourceClass()
         {
            return ClientInvoker.this.declaring;
         }
      };
      for (DynamicFeature feature : invokerConfig.getDynamicFeatures())
      {
         feature.configure(info, new FeatureContextDelegate(invokerConfig));
      }


      this.processors = ProcessorFactory.createProcessors(declaring, method, invokerConfig, config.getDefaultConsumes());
      accepts = MediaTypeHelper.getProduces(declaring, method, config.getDefaultProduces());
      entityExtractorFactory = new DefaultEntityExtractorFactory();
      this.extractor = entityExtractorFactory.createExtractor(method);
   }

   public MediaType[] getAccepts()
   {
      return accepts;
   }

   public Method getMethod()
   {
      return method;
   }

   public Class<?> getDeclaring()
   {
      return declaring;
   }

   public Object invoke(Object[] args)
   {
      ClientInvocation request = createRequest(args);
      ClientResponse response = (ClientResponse)request.invoke();
      ClientContext context = new ClientContext(request, response, entityExtractorFactory);
      return extractor.extractEntity(context);
   }

   protected ClientInvocation createRequest(Object[] args)
   {
	  WebTarget target = this.webTarget;
      for (int i = 0; i < processors.length; i++)
      {
         if (processors != null && processors[i] instanceof WebTargetProcessor)
         {
            WebTargetProcessor processor = (WebTargetProcessor)processors[i];
            target = processor.build(target, args[i]);

         }
      }

      ClientConfiguration parentConfiguration=(ClientConfiguration) target.getConfiguration();
      ClientInvocation clientInvocation = new ClientInvocation(this.webTarget.getResteasyClient(), target.getUri(),
    		  new ClientRequestHeaders(parentConfiguration), parentConfiguration);
      if (accepts != null)
      {
         clientInvocation.getHeaders().accept(accepts);
      }
      for (int i = 0; i < processors.length; i++)
      {
         if (processors != null && processors[i] instanceof InvocationProcessor)
         {
            InvocationProcessor processor = (InvocationProcessor)processors[i];
            processor.process(clientInvocation, args[i]);

         }
      }
      clientInvocation.setMethod(httpMethod);
      return clientInvocation;
   }

   public String getHttpMethod()
   {
      return httpMethod;
   }

   public void setHttpMethod(String httpMethod)
   {
      this.httpMethod = httpMethod;
   }

   public boolean isFollowRedirects()
   {
      return followRedirects;
   }

   public void setFollowRedirects(boolean followRedirects)
   {
      this.followRedirects = followRedirects;
   }

   public void followRedirects()
   {
      setFollowRedirects(true);
   }
}