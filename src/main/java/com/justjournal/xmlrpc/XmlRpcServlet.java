/*
    Copyright (c) 2007 Redstone Handelsbolag

    This library is free software; you can redistribute it and/or modify it under the terms
    of the GNU Lesser General Public License as published by the Free Software Foundation;
    either version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License along with this
    library; if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
    Boston, MA  02111-1307  USA
*/

package com.justjournal.xmlrpc;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.StringTokenizer;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *  Servlet that publishes an XmlRpcServer in a web server.
 *
 *  @author Greger Olsson
 */
public class XmlRpcServlet extends HttpServlet
{

    /** The XmlRpcServer containing the handlers and processors. */
    private XmlRpcServer server;

    /** Indicates whether response messages should be streamed. */
    private boolean streamMessages;

    private String contentType;


    /**
     *  Initializes the servlet by instantiating the XmlRpcServer that will
     *  hold all invocation handlers and processors. The servlet configuration
     *  is read to see if the XML-RPC responses generated should be streamed
     *  immediately to the resonse (non-compliant) or if they should be buffered
     *  before being sent (compliant, since then the Content-Length header may
     *  be set, as stipulated by the XML-RPC specification).<p>
     *  
     *  Further, the configuration is read to see if any services are mentioned
     *  declaratively that are to be instantiated and published. An alternative
     *  to this is to create a new servlet class extending from XmlRpcServlet
     *  that publishes services programmatically.
     */

    public void init( ServletConfig config ) throws ServletException
    {
        String services = config.getInitParameter( "services" );
        String contentType = config.getInitParameter( "contentType" );
        String streamMessages = config.getInitParameter( "streamMessages" );

        if ( streamMessages != null && streamMessages.equals( "1" ) )
        {
            this.streamMessages = true;
        }
        
        if ( contentType != null && contentType.startsWith( "text/javascript+json" ) )
        {
            this.contentType = "text/javascript+json";
            server = new XmlRpcServer( new XmlRpcJsonSerializer() );
        }
        else
        {
            this.contentType = "text/xml";
            server = new XmlRpcServer();
        }
        
        this.contentType += "; charset=" + XmlRpcMessageBundle.getString( "XmlRpcServlet.Encoding" );
        
        if ( services != null )
        {
            addInvocationHandlers( services );
        }
    }

    
    /**
     *  Returns the XmlRpcServer that is backing the servlet.
     * 
     *  @return The XmlRpcServer backing the servlet.
     */
    
    public XmlRpcServer getXmlRpcServer()
    {
        return server;
    }

    
    /**
     *  Indicates whether or not messages are streamed or if they are built in memory
     *  to be able to calculate the HTTP Content-Length.
     * 
     *  @return True if messages are streamed directly over the socket as it is built.
     */
    
    public boolean getStreamMessages()
    {
        return streamMessages;
    }

    
    /**
     *  Returns the content type of the messages returned from the servlet which is
     *  text/xml for XML-RPC messages and text/javascript+json for JSON messages.
     * 
     *  @return The content type of the messages returned from this servlet.
     */
    
    public String getContentType()
    {
        return contentType;
    }


    /**
     * Handles reception and processing of XML-RPC messages via HTTP POST requests.
     * This method sets the appropriate character encoding and content type for the response,
     * then executes the XML-RPC request using the server instance.
     *
     * If streamMessages is true, the response is written directly to the output stream.
     * Otherwise, the response is buffered before being sent, allowing for the Content-Length
     * header to be set (which is compliant with the XML-RPC specification).
     *
     * @param req The HttpServletRequest object containing the XML-RPC request data
     * @param res The HttpServletResponse object used to send the XML-RPC response
     * @throws ServletException If there's an error in servlet execution
     * @throws IOException If there's an error in input/output operations
     */
    public void doPost(
        HttpServletRequest req,
        HttpServletResponse res)
        throws ServletException, IOException
    {
        res.setCharacterEncoding( XmlRpcMessageBundle.getString( "XmlRpcServlet.Encoding" ) );
        res.setContentType( contentType );

        if ( streamMessages )
        {
            server.execute( req.getInputStream(), res.getWriter() );
            res.getWriter().flush();
        }
        else
        {
            Writer responseWriter = new StringWriter( 2048 );
            server.execute( req.getInputStream(), responseWriter );
            String response = responseWriter.toString(); // TODO EXPENSIVE!!
            res.setContentLength( response.length() );

            responseWriter = res.getWriter();
            responseWriter.write( response );
            responseWriter.flush();
        }
    }

    
    /**
     *  Adds invocation handlers based on the service classes mentioned in the supplied
     *  services string.
     * 
     *  @param services List of services specified as fully qualified class names separated
     *                  by whitespace (tabs, spaces, newlines). Each class name is also prefixed
     *                  by the name of the service to use for the class:
     *                  <pre>
     *    <init-param>
     *        <param-name>services</param-name>
     *        <param-value>
     *            SimpleDatabase:java.util.HashMap
     *            RandomNumberGenerator:java.util.Random
     *        </param-value>
     *    </init-param>
     *                  </pre>
     *                  
     *  @throws ServletException If the services argument is invalid or contains names of
     *                           classes that cannot be loaded and instantiated.
     */

    private void addInvocationHandlers( String services ) throws ServletException
    {
        StringTokenizer tokenizer = new StringTokenizer( services );
        
        while ( tokenizer.hasMoreTokens() )
        {
            String service = tokenizer.nextToken();
            int separatorIndex = service.indexOf( ':' );

            if ( separatorIndex > -1 )
            {
                String serviceName = service.substring( 0, separatorIndex );
                String className = service.substring( separatorIndex + 1 );
                
                try
                {
                    Class serviceClass = Class.forName( className );
                    Object invocationHandler = serviceClass.newInstance();
                    server.addInvocationHandler( serviceName, invocationHandler );
                }
                catch ( ClassNotFoundException e )
                {
                    throw new ServletException(
                        XmlRpcMessageBundle.getString(
                            "XmlRpcServlet.ServiceClassNotFound" ) + className, e );
                }
                catch ( InstantiationException e )
                {
                    throw new ServletException(
                        XmlRpcMessageBundle.getString(
                            "XmlRpcServlet.ServiceClassNotInstantiable" ) + className, e );
                }
                catch ( IllegalAccessException e )
                {
                    throw new ServletException(
                        XmlRpcMessageBundle.getString(
                            "XmlRpcServlet.ServiceClassNotAccessible" ) + className, e );
                }
            }
            else
            {
                throw new ServletException(
                    XmlRpcMessageBundle.getString(
                        "XmlRpcServlet.InvalidServicesFormat" ) + services );
            }
        }
    }

}
