<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml" indent="yes"/>

  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="unit[@id='org.scala-ide.scala.library']">
    <xsl:copy>
      <xsl:apply-templates select="@*"/>
      <update id='scala.library' range='' severity='0'/>
      <xsl:apply-templates select="node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="unit[@id='org.scala-ide.scala.compiler']">
    <xsl:copy>
      <xsl:apply-templates select="@*"/>
      <update id='scala.tools.nsc' range='' severity='0'/>
      <xsl:apply-templates select="node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="unit[@id='org.scala-ide.sdt.core']">
    <xsl:copy>
      <xsl:apply-templates select="@*"/>
      <update id='ch.epfl.lamp.sdt.core' range='' severity='0'/>
      <xsl:apply-templates select="node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="unit[@id='org.scala-ide.sdt.aspects']">
    <xsl:copy>
      <xsl:apply-templates select="@*"/>
      <update id='ch.epfl.lamp.sdt.aspects' range='' severity='0'/>
      <xsl:apply-templates select="node()"/>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
