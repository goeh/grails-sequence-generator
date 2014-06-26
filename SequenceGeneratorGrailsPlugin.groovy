/*
 * Copyright (c) 2012 Goran Ehrsson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * under the License.
 */

import grails.plugins.sequence.SequenceEntity
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.jmx.export.MBeanExporter
import org.springframework.jmx.export.annotation.AnnotationJmxAttributeSource
import org.springframework.jmx.export.assembler.MetadataMBeanInfoAssembler
import org.springframework.jmx.support.MBeanServerFactoryBean

import javax.management.MBeanServer
import javax.management.ObjectName
import java.lang.management.ManagementFactory

class SequenceGeneratorGrailsPlugin {
    def version = "1.0"
    def grailsVersion = "2.0 > *"
    def dependsOn = [:]
    def loadAfter = ['domainClass', 'services']
    def pluginExcludes = [
            "grails-app/views/error.gsp",
            "grails-app/domain/grails/plugins/sequence/SequenceTestEntity.groovy"
    ]
    def title = "Sequence Number Generator"
    def author = "Goran Ehrsson"
    def authorEmail = "goran@technipelago.se"
    def description = '''
A Grails service that generate sequence numbers from different sequences, formats, etc.
You can control the starting number, the format and you can have different sequences based on application logic.
The method getNextSequenceNumber() is injected into all domain classes annotated with @SequenceEntity.
It returns the next number for the sequence defined for the domain class.
'''
    def documentation = "https://github.com/goeh/grails-sequence-generator"
    def license = "APACHE"
    def organization = [name: "Technipelago AB", url: "http://www.technipelago.se/"]
    def issueManagement = [system: "github", url: "https://github.com/goeh/grails-sequence-generator/issues"]
    def scm = [url: "https://github.com/goeh/grails-sequence-generator"]

    private Logger LOG = LoggerFactory.getLogger('grails.plugins.sequence.SequenceGeneratorGrailsPlugin')

    private final String JMX_OBJECT_NAME = ':name=SequenceGeneratorService,type=services'

    def doWithSpring = {

        sequenceGenerator(grails.plugins.sequence.DefaultSequenceGenerator) { bean ->
            bean.autowire = 'byName'
        }

        //create/find the mbean server
        mbeanServer(MBeanServerFactoryBean) {
            locateExistingServerIfPossible = true
        }
        //use annotations for attributes/operations
        jmxAttributeSource(AnnotationJmxAttributeSource)
        assembler(MetadataMBeanInfoAssembler) {
            attributeSource = jmxAttributeSource
        }
        //create an exporter that uses annotations
        annotationExporter(MBeanExporter) {
            server = mbeanServer
            assembler = assembler
            beans = [:]
        }
    }

    def doWithApplicationContext = { applicationContext ->
        def config = application.config
        for (c in application.domainClasses) {
            if (c.clazz.getAnnotation(SequenceEntity)) {
                addDomainMethods(applicationContext, config, c.metaClass)
            }
        }
        registerJMX(applicationContext, application.metadata.getApplicationName() + JMX_OBJECT_NAME)
    }

    def onShutdown = { event ->
        unregisterJMX(event.ctx, application.metadata.getApplicationName() + JMX_OBJECT_NAME)
    }

    private void addDomainMethods(ctx, config, MetaClass mc) {
        def service = ctx.getBean('sequenceGeneratorService')
        mc.getNextSequenceNumber = { group = null ->
            def name = delegate.class.simpleName
            def tenant = delegate.hasProperty('tenantId') ? delegate.tenantId : null
            def nbr
            delegate.class.withNewSession {
                nbr = service.nextNumber(name, group, tenant)
            }
            return nbr
        }
    }

    private void registerJMX(ApplicationContext ctx, String jmxObjectName) {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer()
            if (mbs.isRegistered(new ObjectName(jmxObjectName))) {
                LOG.info("MBean $jmxObjectName already registered")
            } else {
                MBeanExporter annotationExporter = ctx.getBean("annotationExporter")
                annotationExporter.beans."$jmxObjectName" = ctx.getBean("sequenceGeneratorService")
                annotationExporter.registerBeans()
            }
        } catch (Exception e) {
            LOG.warn("Failed to register $jmxObjectName", e)
        }
    }

    private void unregisterJMX(ApplicationContext ctx, String jmxObjectName) {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer()
            if (mbs.isRegistered(new ObjectName(jmxObjectName))) {
                mbs.unregisterMBean(new ObjectName(jmxObjectName))
            } else {
                LOG.info("MBean $jmxObjectName not registered")
            }
        } catch (Exception e) {
            LOG.warn("Failed to unregister $jmxObjectName", e)
        }
    }
}
