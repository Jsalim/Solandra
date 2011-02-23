/**
 * Copyright T Jake Luciani
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package solandra;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lucandra.CassandraUtils;

import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.FilterHolder;


public class JettySolandraRunner 
{
  Server server;
  FilterHolder dispatchFilter;
  String context;
  
  public JettySolandraRunner( String context, int port )
  {
    this.init( context, port );
  }

  public JettySolandraRunner( String context, int port, String solrConfigFilename )
  {
    this.init( context, port );
    dispatchFilter.setInitParameter("solrconfig-filename", solrConfigFilename);
  }
  
  private void init( String context, int port )
  {
    this.context = context;
    server = new Server( port );    
    server.setStopAtShutdown( true );

    // Initialize the servlets
    Context root = new Context( server, context, Context.SESSIONS );
    
    // for some reason, there must be a servlet for this to get applied
    root.addServlet( Servlet404.class, "/*" );
    dispatchFilter = root.addFilter( SolandraDispatchFilter.class, "*", Handler.REQUEST );
  }

  //------------------------------------------------------------------------------------------------
  //------------------------------------------------------------------------------------------------
  
  public void start() throws Exception
  {
    start(true);
  }

  public void start(boolean waitForSolr) throws Exception
  {
    if(!server.isRunning() ) {
      server.start();
    }
    if (waitForSolr) waitForSolr(context);
  }


  public void stop() throws Exception
  {
    if( server.isRunning() ) {
      server.stop();
      server.join();
    }
  }

  /** Waits until a ping query to the solr server succeeds,
   * retrying every 200 milliseconds up to 2 minutes.
   */
  public void waitForSolr(String context) throws Exception
  {
    int port = getLocalPort();

    // A raw term query type doesn't check the schema
    URL url = new URL("http://localhost:"+port+context+"/select?q={!raw+f=junit_test_query}ping");

    Exception ex = null;
    // Wait for a total of 20 seconds: 100 tries, 200 milliseconds each
    for (int i=0; i<600; i++) {
      try {
        InputStream stream = url.openStream();
        stream.close();
      } catch (IOException e) {
        // e.printStackTrace();
        ex = e;
        Thread.sleep(200);
        continue;
      }

      return;
    }

    throw new RuntimeException("Jetty/Solr unresponsive",ex);
  }

  /**
   * Returns the Local Port of the first Connector found for the jetty Server.
   * @exception RuntimeException if there is no Connector
   */
  public int getLocalPort() {
    Connector[] conns = server.getConnectors();
    if (0 == conns.length) {
      throw new RuntimeException("Jetty Server has no Connectors");
    }
    return conns[0].getLocalPort();
  }

  //--------------------------------------------------------------
  //--------------------------------------------------------------
    
  /** 
   * This is a stupid hack to give jetty something to attach to
   */
  public static class Servlet404 extends HttpServlet
  {
    @Override
    public void service(HttpServletRequest req, HttpServletResponse res ) throws IOException
    {
      res.sendError( 404, "Can not find: "+req.getRequestURI() );
    }
  }
  
  /**
   * A main class that starts jetty+solr 
   * This is useful for debugging
   */
  public static void main( String[] args )
  {
    try {
      CassandraUtils.startupServer();
        
      JettySolandraRunner jetty = new JettySolandraRunner( "/solandra", 8983 );
      jetty.start();
    }
    catch( Exception ex ) {
      ex.printStackTrace();
    }
  }
}
