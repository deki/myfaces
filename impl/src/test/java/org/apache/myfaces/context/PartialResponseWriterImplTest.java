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

package org.apache.myfaces.context;

import org.apache.myfaces.shared.renderkit.html.HtmlResponseWriterImpl;
import org.apache.myfaces.test.base.AbstractJsfTestCase;

import javax.faces.context.PartialResponseWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Test cases for our impl, which tests for the CDATA nesting
 *
 * @author Werner Punz (latest modification by $Author$)
 * @version $Revision$ $Date$
 */

public class PartialResponseWriterImplTest extends AbstractJsfTestCase {

    static Logger _log = Logger.getLogger(PartialResponseWriterImplTest.class.getName());

    PartialResponseWriterImpl _writer;
    StringWriter _contentCollector;
    private static final String STD_UPDATE_RESULT = "<changes><update id=\"blaId\"><![CDATA[testing]]></update>";
    private static final String CORR_OUTPUT = "checking for correct output: ";

    public PartialResponseWriterImplTest() {
        super("PartialResponseWriterImplTest");
    }

    protected void setUp() throws Exception {
        super.setUp();
        _contentCollector = new StringWriter(100);
    }

    public void testBasicWriteTest() {
        _writer = createTestProbe();
        try {
            //_writer.startCDATA();
            //_writer.startCDATA();
            _writer.write("testing");
            // _writer.endCDATA();
            // _writer.endCDATA();
            _writer.flush();
            _writer.close();
            assertTrue(CORR_OUTPUT, _contentCollector.toString().equals("testing"));
        } catch (IOException e) {
            fail(e.toString());
        }
    }

    public void teststandardNestingTest() {
        _writer = createTestProbe();
        try {
            //_writer.startCDATA();
            _writer.startCDATA();
            _writer.write("testing");
            _writer.endCDATA();
            // _writer.endCDATA();
            _writer.flush();
            _writer.close();
            assertTrue(CORR_OUTPUT, _contentCollector.toString().equals("<![CDATA[testing]]>"));
        } catch (IOException e) {
            fail(e.toString());
        }
    }

    public void testIllegalNestingResolvementTest() {
        _writer = createTestProbe();
        try {
            _writer.startCDATA();
            _writer.startCDATA();
            _writer.write("testing");
            _writer.endCDATA();
            _writer.endCDATA();
            _writer.flush();
            _writer.close();
            assertTrue(CORR_OUTPUT+ _contentCollector.toString(), _contentCollector.toString().equals("<![CDATA[<![CDATA[testing]]><![CDATA[]]]]><![CDATA[>]]>"));
        } catch (IOException e) {
            fail(e.toString());
        }
    }

    public void testIllegalNestingResolvementTest2() {
        _writer = createTestProbe();
        try {
            _writer.startCDATA();
            _writer.startCDATA();
            _writer.write("testing");
            _writer.flush();
            _writer.close();
            assertTrue(CORR_OUTPUT+ _contentCollector.toString(), _contentCollector.toString().equals("<![CDATA[<![CDATA[testing]]>"));
        } catch (IOException e) {
            fail(e.toString());
        }
    }



    public void testStandardUpdate() {
        _writer = createTestProbe();
        try {
            _writer.startUpdate("blaId");
            _writer.write("testing");
            _writer.endUpdate();
            assertTrue(CORR_OUTPUT, _contentCollector.toString().equals(STD_UPDATE_RESULT));
        } catch (IOException e) {
            fail(e.toString());
        }
    }

    public void testStandardUpdateNestedCDATA() {
        _writer = createTestProbe();
        try {
            _writer.startUpdate("blaId");
            _writer.startCDATA();
            _writer.write("testing");
            _writer.endCDATA();
            _writer.endUpdate();
            assertTrue(CORR_OUTPUT+_contentCollector.toString(), _contentCollector.toString().equals("<changes><update id=\"blaId\"><![CDATA[<![CDATA[testing]]><![CDATA[]]]]><![CDATA[>]]></update>"));
        } catch (IOException e) {
            fail(e.toString());
        }
    }


    public void testComponentAuthorNestingFailureTest() {
        _writer = createTestProbe();
        try {
            _writer.startUpdate("blaId");
            _writer.startCDATA();
            _writer.startCDATA();
            _writer.write("testing");
            _writer.endUpdate();
            assertTrue(CORR_OUTPUT+_contentCollector.toString(), _contentCollector.toString().equals("<changes><update id=\"blaId\"><![CDATA[<![CDATA[<![CDATA[testing]]></update>"));
        } catch (IOException e) {
            fail(e.toString());
        }
    }

    public void testStandardInsertAfter() {
        _writer = createTestProbe();
        try {
            _writer.startInsertAfter("blaId");
            _writer.write("testing");
            _writer.endInsert();
            assertTrue(CORR_OUTPUT, _contentCollector.toString().equals("<changes><insert><after id=\"blaId\"><![CDATA[testing]]></after></insert>"));
        } catch (IOException e) {
            fail(e.toString());
        }
    }

    public void testStandardInsertBefore() {
        _writer = createTestProbe();
        try {
            _writer.startInsertBefore("blaId");
            _writer.write("testing");
            _writer.endInsert();
            assertTrue(CORR_OUTPUT, _contentCollector.toString().equals("<changes><insert><before id=\"blaId\"><![CDATA[testing]]></before></insert>"));
        } catch (IOException e) {
            fail(e.toString());
        }
    }

    public void testBrokenUserInput() {
        _writer = createTestProbe();
        try {
            _writer.startInsertBefore("blaId");
            _writer.startElement("input", null);
            _writer.writeAttribute("type","text", null);
            _writer.writeAttribute("value","]]>", null);
            _writer.endElement("input");
            _writer.endInsert();
            assertTrue(CORR_OUTPUT+_contentCollector.toString(), _contentCollector.toString().contains("value=\"]]&gt;\""));
        } catch (IOException e) {
            fail(e.toString());
        }
    }



    public void testDelete() {
        _writer = createTestProbe();
        try {
            _writer.delete("blaId");
            assertTrue(CORR_OUTPUT+_contentCollector.toString(), _contentCollector.toString().equals("<changes><delete id=\"blaId\"></delete>"));
        } catch (IOException e) {
            fail(e.toString());
        }
    }



    /**
     * creates a new test probe (aka response writer)
     *
     * @return
     */
    private PartialResponseWriterImpl createTestProbe() {
        return new PartialResponseWriterImpl(new HtmlResponseWriterImpl(_contentCollector, null, "UTF-8"));
    }

}
