package com.example.weatherapp1.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.weatherapp1.databinding.FragmentHomeBinding
import com.example.weatherapp1.utils.Constants

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
    }

    private fun setupRecyclerView() {
        val adapter = CityAdapter(Constants.DEFAULT_CITIES) { cityName ->
            val action = HomeFragmentDirections.actionHomeToDetail(cityName)
            findNavController().navigate(action)
        }

        binding.rvCities.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
    }

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            val cityName = binding.etCityName.text.toString().trim()
            if (cityName.isNotEmpty()) {
                val action = HomeFragmentDirections.actionHomeToDetail(cityName)
                findNavController().navigate(action)
            } else {
                Toast.makeText(requireContext(), "Masukkan nama kota", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
