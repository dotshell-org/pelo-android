package com.pelotcl.app.core.ui.screens.about

import androidx.compose.runtime.Composable

/**
 * Contrat pour les écrans "À propos"
 * Chaque ville doit fournir sa propre implémentation
 */
interface AboutScreenContract {
    
    /**
     * Nom de l'écran "À propos"
     */
    val screenTitle: String
    
    /**
     * Composant pour l'écran "À propos" principal
     */
    @Composable
    fun AboutScreenContent(
        onBackClick: () -> Unit,
        onCreditsClick: () -> Unit,
        onLegalClick: () -> Unit,
        onContactClick: () -> Unit
    )
    
    /**
     * Composant pour l'écran "Crédits"
     */
    @Composable
    fun CreditsScreenContent(
        onBackClick: () -> Unit,
        onDataSourceClick: () -> Unit,
        onApiSourceClick: () -> Unit
    )
    
    /**
     * Composant pour l'écran "Mentions légales"
     */
    @Composable
    fun LegalScreenContent(
        onBackClick: () -> Unit
    )
    
    /**
     * Composant pour l'écran "Contact"
     */
    @Composable
    fun ContactScreenContent(
        onBackClick: () -> Unit
    )
    
    /**
     * Modèle de données pour une section de crédits
     */
    data class CreditSection(
        val title: String,
        val content: String,
        val links: List<CreditLink>
    )
    
    /**
     * Modèle de données pour un lien dans les crédits
     */
    data class CreditLink(
        val label: String,
        val url: String
    )
    
    /**
     * Modèle de données pour une section légale
     */
    data class LegalSection(
        val title: String,
        val content: String
    )
    
    /**
     * Fournit les sections de crédits spécifiques à la ville
     */
    fun getCreditSections(): List<CreditSection>
    
    /**
     * Fournit les sections légales spécifiques à la ville
     */
    fun getLegalSections(): List<LegalSection>
    
    /**
     * Fournit les informations de contact
     */
    fun getContactInfo(): ContactInfo
    
    /**
     * Modèle de données pour les informations de contact
     */
    data class ContactInfo(
        val email: String?,
        val website: String?,
        val socialMedia: List<SocialMediaLink>
    )
    
    /**
     * Modèle de données pour un lien de réseau social
     */
    data class SocialMediaLink(
        val platform: String,
        val url: String,
        val username: String
    )
}